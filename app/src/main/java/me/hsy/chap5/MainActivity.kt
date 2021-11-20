package me.hsy.chap5

import android.net.TrafficStats
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import me.hsy.chap5.interceptor.TimeConsumeInterceptor
import okhttp3.*
import okhttp3.EventListener
import java.io.IOException
import com.google.gson.GsonBuilder
import me.hsy.chap5.api.*
import me.hsy.chap5.interceptor.NetCacheInterceptor
import java.io.File
import java.lang.Math.round
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private var requestBtn: Button? = null
    private var searchBox: EditText? = null
    private var searchWord: TextView? = null
    private var searchResult: TextView? = null
    private var resultBox: LinearLayout? = null
    private var synoBox: LinearLayout? = null
    private var webBox: LinearLayout? = null
    private var boundary: TextView? = null
    private var pronounce: TextView? = null
    private var traffic: TextView? =null

    private var searchTask: String = "" // The word to look up

    private var client: OkHttpClient? = null
    private var cache: Cache? = null

    private var startByteR: Double = 0.0
    private var startByteS: Double = 0.0

    private val okhttpListener = object : EventListener() {
        override fun dnsStart(call: Call, domainName: String) {
            super.dnsStart(call, domainName)
            runOnUiThread{
                Log.d("@=>", "\nDns Search: $domainName")
            }

        }
        override fun responseBodyStart(call: Call) {
            super.responseBodyStart(call)
            runOnUiThread{
                Log.d("@=>", "\nResponse Start")
            }

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Views
        requestBtn = findViewById<Button>(R.id.request_btn)
        searchBox = findViewById<EditText>(R.id.search_box)
        searchWord = findViewById<TextView>(R.id.search_word)
        resultBox = findViewById<LinearLayout>(R.id.result_box)
        webBox = findViewById<LinearLayout>(R.id.web_box)
        synoBox = findViewById<LinearLayout>(R.id.syno_box)
        boundary = findViewById<TextView>(R.id.boundary)
        pronounce = findViewById<TextView>(R.id.pronounce)
        traffic = findViewById<TextView>(R.id.traffic)

        cache = Cache(File(applicationContext.cacheDir, "request_cache"), maxSize = (50 * 1024 * 1024).toLong())

        client = OkHttpClient
            .Builder()
            .cache(cache)
            .addInterceptor(NetCacheInterceptor())
            .addInterceptor(TimeConsumeInterceptor())
            .eventListener(okhttpListener)
            .build()

        // For traffic statistics
        var uid = applicationInfo.uid
        startByteR = TrafficStats.getUidRxBytes(uid).toDouble()
        startByteS = TrafficStats.getUidTxBytes(uid).toDouble()

        // Listen the change of text in the search box
        searchBox?.addTextChangedListener(object: TextWatcher{
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                searchTask = s.toString()
            }
        } )

        // Search button
        requestBtn?.setOnClickListener {
            // Reset the views
            synoBox?.removeAllViews()
            webBox?.removeAllViews()
            boundary?.visibility = GONE
            pronounce?.visibility = GONE

            if (searchTask != "") {
                searchWord?.text = "搜索中..."
                translate()
                var curByteR: Double = TrafficStats.getUidRxBytes(uid).toDouble()
                var curByteS: Double = TrafficStats.getUidTxBytes(uid).toDouble()
                var rx: Double =
                    ((curByteR - startByteR) / 1024.0 * 10).roundToInt().toDouble() / 10.0
                var sx: Double =
                    ((curByteS - startByteS) / 1024.0 * 10).roundToInt().toDouble() / 10.0

                Log.d("@=>", "Receive: ${rx}")
                Log.d("@=>", "Send: ")
                traffic?.text = "上行流量${sx}KB, 下行流量${rx}KB"
            }
            else {
                runOnUiThread {
                    searchWord?.text = "没输入 哼 哼 啊啊啊啊啊"
                }
            }

        }


    }

    private val gson = GsonBuilder().create()

    private fun request(url: String, callback: Callback){
        val request: Request = Request.Builder()
            .url(url)
            .header("User-Agent", "Hsy-translator")
            .build()
        client!!.newCall(request).enqueue(callback)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val thisContext = this
    private fun translate() {
        val url = "https://dict.youdao.com/jsonapi?q=$searchTask"
        request(url, object: Callback{
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread{
                    searchResult?.text = e.message
                }

            }

            override fun onResponse(call: Call, response: Response) {
                val bodyString = response.body?.string()
                val youdaoBean = gson.fromJson(bodyString, YoudaoBean::class.java)

                if (response.isSuccessful) {
                    if (response.cacheResponse !=null){

                        Log.d("@=>", "$searchTask Response from cache hit")
                    }
                    if (response.networkResponse !=null){

                        Log.d("@=>", "$searchTask Response from network")
                    }
                    runOnUiThread{
                        searchWord?.text = searchTask
                        searchBox?.setText("")
                    }
                    var outterSyno: Syno? = youdaoBean.syno
                    // 释义列表
                    if (outterSyno != null) {
                        var synosList: List<Synos> = outterSyno.synos
                        var pro: String = youdaoBean.simple.word[0].ukphone
                        for (i in synosList.indices) {
                            val meaning = synosList[i].syno.tran
                            val pos = synosList[i].syno.pos
                            val temp = ItemTemplate(thisContext, null, pos, meaning)
                            runOnUiThread {
                                synoBox?.addView(temp)
                            }
                            runOnUiThread {
                                pronounce?.visibility = View.VISIBLE
                                pronounce?.text = "/${pro}/"
                            }
                        }
                    }
                    //网络释义

                    var webTrans: Web_trans? = youdaoBean.web_trans

                    if (webTrans != null){
                        val webList: List<Web_translation> = webTrans.web_translation
                        runOnUiThread {
                            boundary?.visibility = VISIBLE //令分界线可见
                        }
                        for (i in webList.indices) {
                            val pos = webList[i].key
                            val meaning = webList[i].trans[0].value

                            val temp = ItemTemplate(thisContext, null, pos, meaning)
                            runOnUiThread {
                                webBox?.addView(temp)
                            }
                        }
                    }
                    else{
                        val temp = ItemTemplate(thisContext, null, "error", "No results found. Please check your spelling.")
                        runOnUiThread {
                            webBox?.addView(temp)
                        }
                    }
                }
                else {
                    runOnUiThread{
                        searchResult?.text = "failure code: ${response.code}"
                    }
                }
            }
        })
    }
}