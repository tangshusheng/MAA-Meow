package com.aliothmoon.maameow.root;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;

import com.aliothmoon.maameow.third.FakeContext;
import com.aliothmoon.maameow.third.Ln;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import timber.log.Timber;

public final class RootServiceStarter {

    private static final String TAG = "RootServiceStarter";

    private RootServiceStarter() {
    }

    public static void main(String[] args) {
        ParsedArgs parsed = ParsedArgs.parse(args);
        if (parsed == null) {
            System.exit(1);
            return;
        }

        IBinder service = createService(parsed);
        if (service == null) {
            System.exit(1);
            return;
        }

        if (!sendBinder(parsed.packageName, parsed.token, service)) {
            System.exit(1);
            return;
        }

        if (Looper.myLooper() == null) {
            Ln.e("main looper is not prepared");
            System.exit(1);
            return;
        }

        Looper.loop();
        System.exit(0);
    }

    private static IBinder createService(ParsedArgs parsed) {
        try {
            if (parsed.debugName != null) {
                setAppName(parsed.debugName, parsed.userId);
            }

            Context context = createPackageContextAsUser(FakeContext.get(), parsed.packageName, parsed.userId);
            ClassLoader classLoader = context.getClassLoader();
            Class<?> serviceClass = classLoader.loadClass(parsed.className);

            try {
                Constructor<?> constructor = serviceClass.getConstructor(Context.class);
                return (IBinder) constructor.newInstance(context);
            } catch (NoSuchMethodException ignored) {
                Constructor<?> constructor = serviceClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                return (IBinder) constructor.newInstance();
            }
        } catch (Throwable tr) {
            Timber.tag(TAG).e(tr, "unable to create remote service");
            return null;
        }
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private static Context createPackageContextAsUser(Context context, String packageName, int userId)
            throws Exception {
        int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
        try {
            Class<?> userHandleClass = Class.forName("android.os.UserHandle");
            Method ofMethod = userHandleClass.getDeclaredMethod("of", int.class);
            Object userHandle = ofMethod.invoke(null, userId);
            Method createMethod = Context.class.getMethod(
                    "createPackageContextAsUser",
                    String.class,
                    int.class,
                    userHandleClass
            );
            return (Context) createMethod.invoke(context, packageName, flags, userHandle);
        } catch (Throwable ignored) {
            return context.createPackageContext(packageName, flags);
        }
    }

    @SuppressLint("PrivateApi,DiscouragedPrivateApi")
    private static void setAppName(String name, int userId) {
        try {
            Class<?> cls = Class.forName("android.ddm.DdmHandleAppName");
            Method method = cls.getDeclaredMethod("setAppName", String.class, int.class);
            method.invoke(null, name, userId);
        } catch (Throwable tr) {
            Timber.tag(TAG).w(tr, "setAppName failed");
        }
    }

    private static boolean sendBinder(String packageName, String token, IBinder binder) {
        try {
            Bundle extras = new Bundle();
            extras.putString(RootServiceBootstrapRegistry.KEY_TOKEN, token);
            extras.putBinder(RootServiceBootstrapRegistry.KEY_SERVICE_BINDER, binder);

            Uri uri = Uri.parse("content://" + packageName + RootServiceBootstrapRegistry.AUTHORITY_SUFFIX);
            Bundle reply = FakeContext.get().getContentResolver().call(
                    uri,
                    RootServiceBootstrapRegistry.METHOD_ATTACH_REMOTE_SERVICE,
                    null,
                    extras
            );
            if (reply == null) {
                Timber.tag(TAG).e("bootstrap provider returned null");
                return false;
            }

            IBinder lifecycle = reply.getBinder(RootServiceBootstrapRegistry.KEY_APP_BINDER);
            if (lifecycle == null || !lifecycle.pingBinder()) {
                Timber.tag(TAG).e("app lifecycle binder missing");
                return false;
            }

            lifecycle.linkToDeath(() -> {
                Timber.tag(TAG).i("app process died, exiting root service");
                System.exit(0);
            }, 0);
            return true;
        } catch (Throwable tr) {
            Timber.tag(TAG).e(tr, "failed to send binder back to app");
            return false;
        }
    }

    private record ParsedArgs(String token, String packageName, String className, int userId,
                              String debugName) {

        static ParsedArgs parse(String[] args) {
            String token = null;
            String packageName = null;
            String className = null;
            String debugName = null;
            int userId = -1;

            for (String arg : args) {
                if (arg.startsWith("--token=")) {
                    token = arg.substring(8);
                } else if (arg.startsWith("--package=")) {
                    packageName = arg.substring(10);
                } else if (arg.startsWith("--class=")) {
                    className = arg.substring(8);
                } else if (arg.startsWith("--user-id=")) {
                    userId = Integer.parseInt(arg.substring(10));
                } else if (arg.startsWith("--debug-name=")) {
                    debugName = arg.substring(13);
                }
            }

            if (token == null || packageName == null || className == null || userId < 0) {
                Timber.tag(TAG).e("missing required args");
                return null;
            }
            return new ParsedArgs(token, packageName, className, userId, debugName);
        }
    }
}
