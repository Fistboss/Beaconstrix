package com.s3.beaconstrix

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.s3.beaconstrix.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


data class User(
    val name: String,
    val age: Int,
    val city: String
)

data class Response(
    val method: String,
    val path: String,
    val ip: String,
    val parsedBody: User
)

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isTimerRunning = false
    private var hostsImported = false
    private var timerIntervalMillis: Long = 1000 // 1 second default
    private var timerTask: (() -> Unit)? = null // Nullable function
    private val handler = Handler(Looper.getMainLooper())
    private var numberFromList = 0
    private var secondsToCall = 300
    private var counterOfLastChecks = 0
    private var offlineSites = mutableListOf<String>()
    private var msStatistics = mutableListOf<Int>()
    private lateinit var listeningURLS:List<String>
    private val urls = listOf("https://www.google.com", "https://crypto.com", "https://fb.com", "https://instagram.com",
       "https://twitter.com", "https://t.me", "https://linkedin.com", "https://vk.com")

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.button.visibility = View.INVISIBLE
        CoroutineScope(Dispatchers.IO).launch {checkInternet()}
        setTimerInterval(1)
        setTimerTask {
            secondsToCall--
            if(secondsToCall == 0){
                secondsToCall = 300
                counterOfLastChecks++
                if (hostsImported) startCheck()
            }
            binding.intervals1.text = "Counter: $secondsToCall"
        }
        // startCheck()
    }

    private suspend fun isWebsiteAvailable(url: String, again: Boolean,timeout: Int): Boolean {
        //val intervalFromInputTextZombie = Integer.parseInt(binding.intervals1.text.toString())
        return try {
            val startTime = System.currentTimeMillis()
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"  // Запрашиваем только заголовки, а не весь контент
            connection.connectTimeout = timeout*1000 //  таймаут соединения (5000)
            connection.readTimeout = timeout*1000    //  таймаут чтения (5000)
            val responseCode = connection.responseCode
            val endTime = System.currentTimeMillis() // Записываем время окончания
            val responseTime = endTime - startTime // Вычисляем время ответа
            connection.disconnect()
            withContext(Dispatchers.Main) {
                addToResult("$url доступен $responseCode $responseTime ms")
                msStatistics.add(responseTime.toInt())
            }
            true
            //responseCode in 200..399 // Считаем успешным, если код ответа в диапазоне 200-399
        } catch (e: Exception) { //если сайт недоступен то вернем false и уже там повторно прозвоним
            if(again){
            withContext(Dispatchers.Main) {
                    addToResult("$url недоступен при повторном вызове:\n${e.localizedMessage}")
                    offlineSites.add(url)
            }}
            false // Считаем, что сайт недоступен в случае любой ошибки
        }
    }
    private suspend fun checkWebsiteAvailability(url: String,ag: Boolean,timeout: Int): Boolean {
        return withContext(Dispatchers.IO) { isWebsiteAvailable(url,ag,timeout) }
    }

    fun buttonToStart(view: View) {
       // if (!hostsImported) CoroutineScope(Dispatchers.IO).launch {getQueryToLockshield()}
        if (hostsImported) startCheck()
    }

    private fun startCheck() {
        binding.results.text = "$counterOfLastChecks проверок пройдено"
        offlineSites.clear()
        msStatistics.clear()
        if (listeningURLS.isEmpty()) {//urls
            binding.results.text = "Список доменов для прослушивания пуст. Запросите список доменов у управляющего сервера"
            return
        }
        lifecycleScope.launch {
            for (i in listeningURLS.indices) { //urls
                numberFromList = i
                val url = listeningURLS[numberFromList]//urls
                val isAvailable = checkWebsiteAvailability(url,false,5)
                if (!isAvailable){
                    checkWebsiteAvailability(url,true,10)
                }
                if (i==listeningURLS.lastIndex) {//urls
                    withContext(Dispatchers.Main) {
                    if (offlineSites.isEmpty())  addToResult("Все домены доступны")//binding.results.text = "${binding.results.text}\nВсе домены доступны"
                    else {
                        addToResult("${offlineSites.count()} доменов из ${listeningURLS.count()} недоступно")//urls
                      }
                        addToResult("Average ping ${averageMs()} Заряд аккумулятора " + getBatteryPercentage(this@MainActivity).toString())
                        //addToResult(getBatteryPercentage(this@MainActivity).toString())
                    }
                    withContext(Dispatchers.Main) {addToResult("POST запрос не будет выполнен")}
                    //CoroutineScope(Dispatchers.IO).launch { postQueryToLockshield() }
                }
            }
        }
    }

    private suspend fun getQueryToLockshield() {
        hostsImported = false
        withContext(Dispatchers.Main) {addToResult("\nЗапрос хостов...")}
        var url = URL("https://beacon.lockshield.online:4200/api/beacons/tasks")
        try {
            val connection1 = withContext(Dispatchers.IO) { url.openConnection() }
            connection1.connectTimeout = 5000
            connection1.readTimeout = 5000
            BufferedReader(InputStreamReader(connection1.getInputStream())).use { inp ->
                val temp1 = StringBuilder()
                var line: String?
                while (inp.readLine().also { line = it } != null) {
                    temp1.append(line)
                }
                hostsImported = true
                listeningURLS = addUrlsToListFromString(temp1.toString())
                withContext(Dispatchers.Main) {
                    binding.results.text = "${listeningURLS.count()} хостов получено. Нажмите старт."
                    binding.button.visibility = View.VISIBLE
                }

               // startCheck()
            }
        } catch (er1: Exception) {
            withContext(Dispatchers.Main) {
                addToResult("Ошибка https получения хост-списка ${er1.localizedMessage}")
            }
        }
        if (!hostsImported) {
            url = URL("http://beacon.lockshield.online:4200/api/beacons/tasks")
            // url = URL("https://echo.free.beeceptor.com/sample-request?author=beeceptor")
            try {
                val connection1 = withContext(Dispatchers.IO) { url.openConnection() }
                connection1.connectTimeout = 10000
                connection1.readTimeout = 10000
                BufferedReader(InputStreamReader(connection1.getInputStream())).use { inp ->
                    val temp1 = StringBuilder()
                    var line: String?
                    while (inp.readLine().also { line = it } != null) {
                        temp1.append(line)
                    }
                    hostsImported = true
                    listeningURLS = addUrlsToListFromString(temp1.toString())
                    withContext(Dispatchers.Main) {
                        binding.results.text = "${listeningURLS.count()} хостов получено. Нажмите старт."
                        binding.button.visibility = View.VISIBLE
                    }

                    //startCheck()
                }
            } catch (er1: Exception) {
                withContext(Dispatchers.Main) {
                    addToResult("Ошибка http получения хост-списка ${er1.localizedMessage}")
                }
            }
        }
    }

    private fun addUrlsToListFromString(response: String): List<String> {
        // 1. Удаляем квадратные скобки в начале и конце строки (если они есть).
        val trimmedResponse = response.removePrefix("[").removeSuffix("]")

        // 2. Разделяем строку на отдельные URL по запятой и пробелу.
        val urls = trimmedResponse.split(",")

        // 3. Убираем кавычки вокруг каждого URL и возвращаем список.
        return urls.map { it.trim('"').trim() }
    }

    private suspend fun postQueryToLockshield() {//http https в манифесте cleartextHTTP
        val url1 = URL("http://echo.free.beeceptor.com/sample-request?author=beeceptor")
        lateinit var connection: HttpURLConnection
        try {
            connection = withContext(Dispatchers.IO) {
                (url1.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
            }
            val requestBody = "{\"name\": \"John Doe\", \"age\": \"30\", \"city\": \"New York\"}"
            withContext(Dispatchers.IO) { // Отправка данных в IO
                connection.outputStream.use { outputStream ->
                    outputStream.write(requestBody.toByteArray())
                }
            }
            val responseCode = withContext(Dispatchers.IO) { connection.responseCode }
            withContext(Dispatchers.Main) {
                addToResult("Post response code: $responseCode")
            }
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = withContext(Dispatchers.IO) {
                    val inputStream: InputStream = connection.inputStream
                    try {
                        ByteArrayOutputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                            outputStream.toString(Charsets.UTF_8.name())
                        }
                    } catch (e: IOException) {
                        addToResult("Read error: ${e.message}")
                    } finally {
                        inputStream.close()  // Закрываем поток
                    }

                }
                withContext(Dispatchers.Main) { addToResult("Ответ на post запрос: $response") }
                val json = Json { ignoreUnknownKeys = true }

                try {
                    val response1: Response = json.decodeFromString(response.toString())
                    val name1 = response1.parsedBody.name
                    val age1 = response1.parsedBody.age
                    val city1 = response1.parsedBody.city
                    withContext(Dispatchers.Main) {
                        addToResult("Name: $name1, Age: $age1, City: $city1")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        addToResult("Ошибка JSON: ${e.message}")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    addToResult("Ошибка: ${responseCode}")
                }
               }
        } catch (er1: Exception) {
            withContext(Dispatchers.Main) {
                addToResult("Ошибка POST: ${er1.localizedMessage}")
            }
        } finally {
            connection.disconnect()
        }
    }
    private fun averageMs(): String {
        if (msStatistics.isEmpty()) {
            return "0" // Или другое значение по умолчанию, если список пуст
        }
        var sum = 0
        for (num in msStatistics) {
            sum += num
        }
        val average = sum / msStatistics.size
        return average.toString()
    }
    private fun addToResult(string: String){
        binding.results.text = "${binding.results.text}\n${string}"
    }
    private fun getBatteryPercentage(context: Context): Float {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, iFilter)

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return level * 100 / scale.toFloat()
    }
    private suspend fun checkInternet(){
        try {
            val connection2 = URL( "https://www.google.com").openConnection() as HttpURLConnection
            connection2.requestMethod = "HEAD"  // Запрашиваем только заголовки, а не весь контент
            connection2.connectTimeout = 5000 //  таймаут соединения (5000)
            connection2.readTimeout = 4000    //  таймаут чтения (5000)
            val responseCode = connection2.responseCode
            connection2.disconnect()
            if (responseCode == 200) {
               CoroutineScope(Dispatchers.IO).launch {getQueryToLockshield()}
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
               binding.results.text = "Невозможно запустить.\nПроверьте включен ли интернет"
            }
        }
    }
    private fun setTimerInterval(intervalSeconds: Long) {   // Функция установки интервала
        timerIntervalMillis = intervalSeconds * 1000
    }

    private fun setTimerTask(task: () -> Unit) {  // Функция установки задачи, выполняемой таймером
        timerTask = task
    }

    private fun startTimer() { // Функция запуска таймера
        if (!isTimerRunning && timerTask != null) {
            isTimerRunning = true
            handler.postDelayed(object : Runnable {
                override fun run() {
                    timerTask?.invoke()  // Execute task
                    if (isTimerRunning) {
                        handler.postDelayed(this, timerIntervalMillis) // Schedule next run
                    }
                }
            }, timerIntervalMillis)
        }
    }
    private fun stopTimer() {// Функция остановки таймера
        isTimerRunning = false
        handler.removeCallbacksAndMessages(null) // Удалить все отложенные задачи
    }

    override fun onResume() {
        super.onResume()
        startTimer() // Запуск таймера
    }

    override fun onPause() {
        super.onPause()
        stopTimer() // Остановка таймера
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer() // Гарантированная остановка таймера
    }
}
