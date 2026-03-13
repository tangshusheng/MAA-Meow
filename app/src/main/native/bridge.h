#ifndef NATIVE_LIB_H
#define NATIVE_LIB_H

#include <jni.h>
#include <cstdint>
#include <cstddef>

#ifdef __cplusplus
extern "C" {
#endif

#define BRIDGE_API __attribute__((visibility("default")))

// Frame info structure - for MaaCore to read screen frames
typedef struct {
    uint32_t width;
    uint32_t height;
    uint32_t stride;
    uint32_t length;
    void *data;
    void *frame_ref;
} FrameInfo;

// 帧缓冲状态
typedef enum {
    FRAME_STATE_FREE = 0,
    FRAME_STATE_WRITING = 2
} FrameBufferState;

// 帧缓冲数量
#define FRAME_BUFFER_COUNT 3

typedef struct {
    uint8_t *data;           // RGBA
    uint8_t *bgr_data;       // BGR
    int width;
    int height;
    int stride;
    size_t size;
    size_t bgr_size;
    int64_t timestamp;
    int64_t frameCount;
} FrameBuffer;

// Unified method type
typedef enum {
    START_GAME = 1,
    STOP_GAME = 2,
    INPUT = 4,
    TOUCH_DOWN = 6,
    TOUCH_MOVE = 7,
    TOUCH_UP = 8,
    KEY_DOWN = 9,
    KEY_UP = 10
} MethodType;

typedef struct {
    int x;
    int y;
} Position;

typedef struct {
    const char *package_name;
    int force_stop;
} StartGameArgs;

typedef struct {
    const char *client_type;
} StopGameArgs;

typedef struct {
    const char *text;
} InputArgs;

typedef struct {
    Position p;
} TouchArgs;

typedef struct {
    int key_code;
} KeyArgs;

typedef union {
    StartGameArgs start_game;
    StopGameArgs stop_game;
    InputArgs input;
    TouchArgs touch;
    KeyArgs key;
} ArgUnion;

typedef struct {
    int display_id;
    MethodType method;
    ArgUnion args;
} MethodParam;

BRIDGE_API void *AttachThread(void);

BRIDGE_API int DetachThread(void *env);

BRIDGE_API FrameInfo GetLockedPixels(void);

BRIDGE_API int UnlockPixels(FrameInfo info);

BRIDGE_API int DispatchInputMessage(MethodParam param);

// ---------------------------------------------------------
// 内部管理函数，通过 JNI RegisterNatives 绑定，不需要导出
// ---------------------------------------------------------

void InitFrameBuffers(int width, int height);
void ReleaseFrameBuffers(void);
int64_t CopyFrameFromHardwareBuffer(void *env, void *hardwareBufferObj, int64_t timestampNs);

#ifdef __cplusplus
}

bool CheckJNIException(JNIEnv *env, const char *context);

#endif

#endif // NATIVE_LIB_H
