/*
 * AppErrorsTracking - Added more features to app's crash dialog, fixed custom rom deleted dialog, the best experience to Android developer.
 * Copyright (C) 2019-2022 Fankes Studio(qzmmcn@163.com)
 * https://github.com/KitsunePie/AppErrorsTracking
 *
 * This software is non-free but opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 *
 * This file is Created by fankes on 2022/5/7.
 */
@file:Suppress("DEPRECATION", "UseCompatLoadingForDrawables")

package com.fankes.apperrorstracking.hook.entity

import android.app.AlertDialog
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Message
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.fankes.apperrorstracking.R
import com.fankes.apperrorstracking.utils.drawable.drawabletoolbox.DrawableBuilder
import com.fankes.apperrorstracking.utils.factory.dp
import com.fankes.apperrorstracking.utils.factory.isSystemInDarkMode
import com.fankes.apperrorstracking.utils.factory.openApp
import com.fankes.apperrorstracking.utils.factory.openSelfSetting
import com.highcapable.yukihookapi.hook.bean.VariousClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.loggerE
import com.highcapable.yukihookapi.hook.type.android.MessageClass

class FrameworkHooker : YukiBaseHooker() {

    companion object {

        private const val AppErrorsClass = "com.android.server.am.AppErrors"

        private const val AppErrorResultClass = "com.android.server.am.AppErrorResult"

        private const val AppErrorDialog_DataClass = "com.android.server.am.AppErrorDialog\$Data"

        private const val ProcessRecordClass = "com.android.server.am.ProcessRecord"

        private val ErrorDialogControllerClass = VariousClass(
            "com.android.server.am.ProcessRecord\$ErrorDialogController",
            "com.android.server.am.ErrorDialogController"
        )
    }

    /**
     * 创建对话框按钮
     * @param context 实例
     * @param drawableId 按钮图标
     * @param content 按钮文本
     * @param it 点击事件回调
     * @return [LinearLayout]
     */
    private fun createButtonItem(context: Context, drawableId: Int, content: String, it: () -> Unit) =
        LinearLayout(context).apply {
            background = DrawableBuilder().rounded().cornerRadius(15.dp(context)).ripple().rippleColor(0xFFAAAAAA.toInt()).build()
            gravity = Gravity.CENTER or Gravity.START
            layoutParams =
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(ImageView(context).apply {
                setImageDrawable(moduleAppResources.getDrawable(drawableId))
                layoutParams = ViewGroup.LayoutParams(25.dp(context), 25.dp(context))
                setColorFilter(if (context.isSystemInDarkMode) Color.WHITE else Color.BLACK)
            })
            addView(View(context).apply { layoutParams = ViewGroup.LayoutParams(15.dp(context), 0) })
            addView(TextView(context).apply {
                text = content
                textSize = 16f
                setTextColor(if (context.isSystemInDarkMode) 0xFFDDDDDD.toInt() else 0xFF777777.toInt())
            })
            setPadding(19.dp(context), 16.dp(context), 19.dp(context), 16.dp(context))
            setOnClickListener { it() }
        }

    override fun onHook() {
        /** 干掉原生错误对话框 - 如果有 */
        ErrorDialogControllerClass.hook {
            injectMember {
                method {
                    name = "hasCrashDialogs"
                    emptyParam()
                }
                replaceToTrue()
            }
            injectMember {
                method {
                    name = "showCrashDialogs"
                    paramCount = 1
                }
                intercept()
            }
        }
        /** 注入自定义错误对话框 */
        AppErrorsClass.hook {
            injectMember {
                method {
                    name = "handleShowAppErrorUi"
                    param(MessageClass)
                }
                afterHook {
                    /** 当前实例 */
                    val context = field { name = "mContext" }.get(instance).cast<Context>() ?: return@afterHook

                    /** 错误数据 */
                    val errData = args().first().cast<Message>()?.obj

                    /** 错误结果 */
                    val errResult = AppErrorResultClass.clazz.method {
                        name = "get"
                        emptyParam()
                    }.get(AppErrorDialog_DataClass.clazz.field {
                        name = "result"
                    }.get(errData).any()).int()

                    /** 当前 APP 信息 */
                    val appInfo = ProcessRecordClass.clazz.field { name = "info" }
                        .get(AppErrorDialog_DataClass.clazz.field { name = "proc" }
                            .get(errData).any()).cast<ApplicationInfo>() ?: ApplicationInfo()

                    /** 是否短时内重复错误 */
                    val isRepeating = AppErrorDialog_DataClass.clazz.field { name = "repeating" }.get(errData).boolean()
                    /** 判断在后台就不显示对话框 */
                    if (errResult == -2) return@afterHook
                    /** 创建自定义对话框 */
                    AlertDialog.Builder(
                        context, if (context.isSystemInDarkMode)
                            android.R.style.Theme_Material_Dialog
                        else android.R.style.Theme_Material_Light_Dialog
                    ).create().apply {
                        setTitle("${appInfo.loadLabel(context.packageManager)} ${if (isRepeating) "屡次停止运行" else "已停止运行"}")
                        setView(LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(createButtonItem(context, R.drawable.ic_baseline_info, content = "应用信息") {
                                cancel()
                                context.openSelfSetting(packageName = appInfo.packageName)
                            })
                            if (isRepeating)
                                addView(createButtonItem(context, R.drawable.ic_baseline_close, content = "关闭应用") { cancel() })
                            else addView(createButtonItem(context, R.drawable.ic_baseline_refresh, content = "重新打开") {
                                cancel()
                                context.openApp(appInfo.packageName)
                            })
                            addView(createButtonItem(context, R.drawable.ic_baseline_bug_report, content = "错误详情") {
                                // TODO 待开发
                            })
                            setPadding(6.dp(context), 15.dp(context), 6.dp(context), 6.dp(context))
                        })
                        /** 只有 SystemUid 才能响应系统级别的对话框 */
                        window?.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                    }.show()
                    /** 打印错误日志 */
                    loggerE(msg = "Process \"${appInfo.packageName}\" has crashed, isRepeating --> $isRepeating")
                }
            }
        }
    }
}