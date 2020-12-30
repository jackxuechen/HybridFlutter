package com.cc.hybrid.bridge.js

import com.cc.hybrid.Logger
import com.cc.hybrid.event.EventManager
import com.cc.hybrid.util.LoadingUtil
import com.cc.hybrid.util.SpUtil
import com.cc.hybrid.util.ToastUtil
import com.cc.hybrid.v8.V8Manager
import com.cc.hybrid.v8.V8Util
import com.eclipsesource.v8.JavaCallback
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Function
import com.eclipsesource.v8.V8Object
import org.json.JSONObject
import java.util.*

object JSPageManager {

    private val v8PageDictionary: MutableMap<String, V8Object> = mutableMapOf()

    private fun prepareScriptContent(content: String): String {
        return content.replace("\n", "")
    }

    /**
     * 把page的Script注入到JSCore中对应的page下
     * @param script html生成json文件中script的节点
     * */
    fun attachPageScriptToJsCore(pageId: String, script: String) {
        attachPage(pageId, prepareScriptContent(script))
    }

    /**
     * 保存模块信息到JsCore，作为上下文维护在应用中，存在整个应用的生命周期
     */
    private fun attachPage(pageId: String, script: String) {
        try {
            V8Manager.executeScript("global.loadPage('$pageId')")
            val realPageObject = getV8Page(pageId)
            if (null != realPageObject) {
                // 将js对象注入到临时page
                realPageObject.executeJSFunction("__native__evalInPage", script)

                // 将临时page添加到RealPage里面
                val page = V8Manager.v8.getObject("page")
                page.setPrototype(realPageObject)
                page.keys.forEach {
                    realPageObject.add(it, page.getObject(it))
                    realPageObject.setPrototype(page.getObject(it))
                }
                realPageObject.registerJavaMethod(JavaCallback { receiver, parameters ->
                    val localPageId = receiver.getString("pageId")
                    val data = parameters?.getString(0)
                    onRefresh(localPageId, data)
                    receiver as Any
                }, "__native__refresh")

                val wx = realPageObject.getObject("wx")
                wx.registerJavaMethod(JavaCallback { receiver, parameters ->
                    val localPageId = receiver.getString("pageId")
                    val data = parameters?.getObject(0)
                    if (null != data && data.contains("title")) {
                        EventManager.instance.sendMessage(what = EventManager.TYPE_NAVIGATION_BAR_TITLE, pageId = localPageId, obj = data.getString("title"))
                    }
                    receiver as Any
                }, "setNavigationBarTitle")
                wx.registerJavaMethod(JavaCallback { receiver, parameters ->
                    val localPageId = receiver.getString("pageId")
                    val data = parameters?.getObject(0)
                    if (null != data && data.contains("backgroundColor")) {
                        EventManager.instance.sendMessage(what = EventManager.TYPE_NAVIGATION_BAR_COLOR, pageId = localPageId, obj = data.getString("backgroundColor"))
                    }
                    receiver as Any
                }, "setNavigationBarColor")
                wx.registerJavaMethod(JavaCallback { receiver, parameters ->
                    val localPageId = receiver.getString("pageId")
                    val data = parameters?.getObject(0)
                    if (null != data && data.contains("backgroundColor")) {
                        EventManager.instance.sendMessage(what = EventManager.TYPE_BACKGROUND_COLOR, pageId = localPageId, obj = data.getString("backgroundColor"))
                    }
                    receiver as Any
                }, "setBackgroundColor")
                wx.registerJavaMethod(JavaCallback { receiver, parameters ->
                    val localPageId = receiver.getString("pageId")
                    val data = parameters?.getObject(0)
                    if (null != data) {
                        val jsonObject = JSONObject()
                        data.keys.forEach {
                            jsonObject.put(it, data.get(it))
                        }
                        EventManager.instance.sendMessage(what = EventManager.TYPE_NAVIGATE_TO, pageId = localPageId, obj = jsonObject.toString())
                    }
                    receiver as Any
                }, "navigateTo")

                wx.registerJavaMethod(JavaCallback { receiver, parameters ->
                    val localPageId = receiver.getString("pageId")
                    val data = parameters?.getObject(0)
                    data?.add("pageId", localPageId)
                    data?.add("requestId", UUID.randomUUID().toString())
                    wx.getObject("requestData").add(data?.getString("requestId"), data)
                    JSNetwork().request(data!!)
                    receiver as Any
                }, "request")

                wx.registerJavaMethod(JavaCallback { receiver, parameters ->
                    val data = parameters?.getObject(0)
                    LoadingUtil.showLoading(data)
                    receiver as Any
                }, "showLoading")

                wx.registerJavaMethod(JavaCallback { receiver, parameters ->
                    val localPageId = receiver.getString("pageId")
                    EventManager.instance.sendMessage(what = EventManager.TYPE_START_PULL_DOWN_REFRESH, pageId = localPageId, obj = "")
                    receiver as Any
                }, "startPullDownRefresh")

                wx.registerJavaMethod(JavaCallback { receiver, parameters ->
                    val localPageId = receiver.getString("pageId")
                    EventManager.instance.sendMessage(what = EventManager.TYPE_STOP_PULL_DOWN_REFRESH, pageId = localPageId, obj = "")
                    receiver as Any
                }, "stopPullDownRefresh")

                wx.registerJavaMethod(JavaCallback { receiver, parameters ->
                    LoadingUtil.hideLoading()
                    receiver as Any
                }, "hideLoading")

                wx.registerJavaMethod(JavaCallback { receiver, parameters ->
                    val data = parameters?.getObject(0)
                    ToastUtil.showToast(data)
                    receiver as Any
                }, "showToast")

                wx.registerJavaMethod(JavaCallback { receiver, parameters ->
                    ToastUtil.hideToast()
                    receiver as Any
                }, "hideToast")

                wx.registerJavaMethod(JavaCallback { receiver, parameters ->
                    val data = parameters?.getObject(0)
                    val key = data?.getString("key")
                    val value = data?.get("data")
                    if (null != key && null != value) {
                        SpUtil.put(key, value)
                    }
                    receiver as Any
                }, "setStorage")

                wx.registerJavaMethod(JavaCallback { receiver, parameters ->
                    val data = parameters?.getObject(0)
                    val key = data?.getString("key")
                    val success = data?.getObject("success")
                    val complete = data?.getObject("complete")
                    if (null != key && null != success) {
                        try {
                            val value = SpUtil.get(key)
                            if (success is V8Function) {
                                success.call(receiver, V8Array(V8Manager.v8).push(value))
                            }
                        } catch (e: Exception) {
                            val fail = data.getObject("fail")
                            if (null != fail) {
                                if (fail is V8Function) {
                                    fail.call(receiver, V8Array(V8Manager.v8))
                                }
                            }
                        }
                    }
                    if (null != complete) {
                        if (complete is V8Function) {
                            complete.call(receiver, V8Array(V8Manager.v8))
                        }
                    }
                    receiver as Any
                }, "getStorage")
            }
        } catch (e: Exception) {
            Logger.printError(e)
        }
    }

    @Synchronized
    fun onNetworkResult(pageId: String, requestId: String, success: String, json: String) {
        try {
            getV8Page(pageId)?.getObject("wx")?.executeJSFunction("onNetworkResult", requestId, success, json)
        } catch (e: Exception) {
            Logger.printError(e)
        }
    }

    @Synchronized
    fun onRefresh(pageId: String, json: String?) {
//        Logger.d("JSPageManager", "onRefresh pageId = $pageId")
        EventManager.instance.sendMessage(what = EventManager.TYPE_REFRESH, pageId = pageId, obj = json
                ?: "")
    }

    private fun getV8Page(pageId: String): V8Object? {
        val cache = v8PageDictionary[pageId]
        if (null != cache && !cache.isReleased && !cache.isUndefined) {
            return cache
        } else {
            v8PageDictionary.remove(pageId)
        }
        val page = getPage(pageId)
        return if (page is V8Object && !page.isUndefined && !page.isReleased) {
            v8PageDictionary[pageId] = page
            page
        } else {
            null
        }
    }

    private fun getPage(pageId: String): Any? {
        return V8Manager.executeScript("this.getPage('$pageId')")
    }

    fun callMethodInPage(pageId: String, method: String, vararg args: String?, executeListener: ((Throwable?) -> Unit)? = null) {
        try {
            val page = getV8Page(pageId)
            if (method.isNotEmpty() && null != page && !page.isUndefined && page.contains(method)) {
                val params = V8Array(page.runtime)

                args.forEach {
                    if (!it.isNullOrEmpty()) {
                        val json = V8Manager.v8.getObject("JSON")
                        val param = json.executeJSFunction("parse", it)
                        when (param) {
                            is V8Array -> params.push(param)
                            is V8Object -> params.push(param)
                            is String -> params.push(param)
                            is Int -> params.push(param)
                            is Double -> params.push(param)
                            is Boolean -> params.push(param)
                        }
                    }
                }
                (page.get(method) as V8Function).call(page, params)
            }
            if (executeListener != null) {
                executeListener(null)
            }
        } catch (e: Exception) {
            Logger.e("callMethodInPage", "$method : error ${e.message}")
        }
    }

    fun callback(callbackId: String) {
        try {
            V8Manager.v8.executeVoidFunction("callback", V8Array(V8Manager.v8).push(callbackId))
        } catch (e: Exception) {
            Logger.printError(e)
        }
    }

    fun onInitComplete(pageId: String) {
        val page = getV8Page(pageId)
        try {
            page?.executeVoidFunction("__native__initComplete", V8Array(V8Manager.v8))
        } catch (e: Exception) {
            Logger.printError(e)
        }
    }

    fun handleRepeat(pageId: String, componentId: String, type: String, key: String, watch: Boolean, expression: String): Int? {
        val page = getV8Page(pageId)
        return try {
            page?.executeIntegerFunction("__native__getExpValue", V8Array(V8Manager.v8).push(componentId).push(type).push(key).push(watch).push(expression))
        } catch (e: Exception) {
            Logger.printError(e)
            0
        }
    }

    fun handleExpression(pageId: String, componentId: String, type: String, key: String, watch: Boolean, expression: String): String? {
        val page = getV8Page(pageId)
        val result: Any? = try {
            page?.executeFunction("__native__getExpValue", V8Array(V8Manager.v8).push(componentId).push(type).push(key).push(watch).push(expression))
        } catch (e: Exception) {
            Logger.printError(e)
            ""
        }
        return result?.toString()
    }

    fun removeObserver(pageId: String, ids: List<String>) {
        val page = getV8Page(pageId)
        try {
            page?.executeVoidFunction("__native__removeObserverByIds", V8Array(V8Manager.v8).push(V8Util.toV8Array(V8Manager.v8, ids)))
        } catch (e: Exception) {
            Logger.printError(e)
        }
    }

    fun removePage(pageId: String) {
        try {
            V8Manager.executeScript("global.removePage('$pageId')")
        } catch (e: Exception) {
            Logger.printError(e)
        }
    }
}