package com.aliothmoon.maameow.presentation.components

import android.text.InputType
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext


/**
 * 内部使用本地状态缓冲，防止上游异步状态更新（如 DataStore / StateFlow）
 * 导致 TextField 光标跳转。
 *
 * 原理：TextField 始终绑定同步的 localValue，用户输入立即生效；
 * dirty 标记防止异步回写的中间值覆盖用户正在输入的内容。
 *
 * @see <a href="https://medium.com/androiddevelopers/effective-state-management-for-textfield-in-compose-d6e5b070fbe5">
 *   Effective state management for TextField in Compose</a>
 */
@Composable
private fun rememberBufferedTextState(
    externalValue: String,
    onExternalChange: (String) -> Unit
): Pair<String, (String) -> Unit> {
    var localValue by remember { mutableStateOf(externalValue) }
    var dirty by remember { mutableStateOf(false) }

    // 外部值变化且用户未在输入中 -> 同步（如初始加载、编程式重置）
    if (!dirty && localValue != externalValue) {
        localValue = externalValue
    }
    // 上游值追上本地值 -> 清除 dirty，恢复外部同步能力
    if (dirty && externalValue == localValue) {
        dirty = false
    }

    val onValueChange: (String) -> Unit = { newText ->
        dirty = true
        localValue = newText
        onExternalChange(newText)
    }

    return localValue to onValueChange
}


@Composable
fun ITextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true,
    supportingText: @Composable (() -> Unit)? = null,
    outlineColor: Color? = null,
    onImeAction: (() -> Unit)? = null
) {
    val isInFloatingWindow = LocalFloatingWindowContext.current
    val (bufferedValue, bufferedOnChange) = rememberBufferedTextState(value, onValueChange)

    if (isInFloatingWindow) {
        // 悬浮窗环境：使用 FloatWindowEditText
        FloatWindowEditText(
            value = bufferedValue,
            onValueChange = bufferedOnChange,
            modifier = modifier.fillMaxWidth(),
            label = label,
            hint = placeholder,
            singleLine = singleLine,
            enabled = enabled,
            inputType = if (singleLine) InputType.TYPE_CLASS_TEXT else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )
    } else {
        // 普通环境：使用 OutlinedTextField
        OutlinedTextField(
            value = bufferedValue,
            onValueChange = bufferedOnChange,
            modifier = modifier.fillMaxWidth(),
            label = label?.let { { Text(it) } },
            placeholder = { Text(placeholder) },
            singleLine = singleLine,
            enabled = enabled,
            supportingText = supportingText
        )
    }
}


@Composable
fun ITextFieldWithFocus(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true,
    supportingText: @Composable (() -> Unit)? = null
) {
    val isInFloatingWindow = LocalFloatingWindowContext.current
    val (bufferedValue, bufferedOnChange) = rememberBufferedTextState(value, onValueChange)

    if (isInFloatingWindow) {
        // 悬浮窗环境：使用 FloatWindowEditText
        FloatWindowEditText(
            value = bufferedValue,
            onValueChange = bufferedOnChange,
            modifier = modifier.fillMaxWidth(),
            label = label,
            hint = placeholder,
            singleLine = singleLine,
            enabled = enabled,
            inputType = if (singleLine) InputType.TYPE_CLASS_TEXT else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            onFocusChange = { hasFocus ->
                if (!hasFocus) {
                    onFocusLost()
                }
            }
        )
    } else {
        // 普通环境：使用 OutlinedTextField + onFocusChanged
        OutlinedTextField(
            value = bufferedValue,
            onValueChange = bufferedOnChange,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        onFocusLost()
                    }
                },
            label = label?.let { { Text(it) } },
            placeholder = { Text(placeholder) },
            singleLine = singleLine,
            enabled = enabled,
            supportingText = supportingText
        )
    }
}
