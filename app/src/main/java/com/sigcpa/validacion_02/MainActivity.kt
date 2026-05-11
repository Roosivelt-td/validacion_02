package com.sigcpa.validacion_02

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.card.MaterialCardView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // Componentes UI
    private lateinit var txtEstadoBT: TextView
    private lateinit var txtTemperatura: TextView
    private lateinit var txtHumedad: TextView
    private lateinit var txtIconoEstado: TextView
    private lateinit var txtEstadoClima: TextView
    private lateinit var txtMensajeAlerta: TextView
    private lateinit var btnConectar: Button
    private lateinit var statusDot: View
    private lateinit var dataCard: MaterialCardView
    private lateinit var mainLayout: ConstraintLayout

    // Bluetooth
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var isConnected = false
    private var readingThread: Thread? = null

    // UUID estándar para SPP (Serial Port Profile)
    private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Handler para actualizar UI
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar vistas
        txtEstadoBT = findViewById(R.id.txtEstadoBT)
        txtTemperatura = findViewById(R.id.txtTemperatura)
        txtHumedad = findViewById(R.id.txtHumedad)
        txtIconoEstado = findViewById(R.id.txtIconoEstado)
        txtEstadoClima = findViewById(R.id.txtEstadoClima)
        txtMensajeAlerta = findViewById(R.id.txtMensajeAlerta)
        btnConectar = findViewById(R.id.btnConectar)
        statusDot = findViewById(R.id.statusDot)
        dataCard = findViewById(R.id.dataCard)
        mainLayout = findViewById(R.id.mainLayout)

        btnConectar.setOnClickListener {
            if (isConnected) {
                desconectarBluetooth()
            } else {
                verificarPermisosYConectar()
            }
        }

        verificarPermisosIniciales()
    }

    private fun verificarPermisosIniciales() {
        if (bluetoothAdapter == null) {
            txtEstadoBT.text = "Error: Dispositivo sin Bluetooth"
            Toast.makeText(this, "Este dispositivo no soporta Bluetooth", Toast.LENGTH_LONG).show()
            btnConectar.isEnabled = false
        }
    }

    private fun verificarPermisosYConectar() {
        val permisos = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permisos.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                permisos.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                != PackageManager.PERMISSION_GRANTED) {
                permisos.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                != PackageManager.PERMISSION_GRANTED) {
                permisos.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permisos.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permisos.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permisos.toTypedArray(), REQUEST_BLUETOOTH_PERMISSIONS)
        } else {
            conectarBluetooth()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                conectarBluetooth()
            } else {
                Toast.makeText(this, "Permisos necesarios para Bluetooth", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun conectarBluetooth() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no disponible", Toast.LENGTH_LONG).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Por favor activa Bluetooth en el dispositivo", Toast.LENGTH_LONG).show()
            txtEstadoBT.text = "Bluetooth apagado"
            return
        }

        txtEstadoBT.text = "Buscando dispositivo HC-05/HC-06..."

        // Buscar dispositivo emparejado
        var dispositivoEncontrado: BluetoothDevice? = null
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            verificarPermisosYConectar()
            return
        }

        val dispositivosEmparejados = bluetoothAdapter.bondedDevices
        
        for (device in dispositivosEmparejados) {
            if (device.name != null && (device.name.contains("HC-05") || device.name.contains("HC-06") || device.name.contains("HC"))) {
                dispositivoEncontrado = device
                break
            }
        }

        if (dispositivoEncontrado == null && dispositivosEmparejados.isNotEmpty()) {
            dispositivoEncontrado = dispositivosEmparejados.first()
            txtEstadoBT.text = "Usando: ${dispositivoEncontrado?.name}"
        }

        if (dispositivoEncontrado == null) {
            txtEstadoBT.text = "Error: Empareja HC-05/HC-06 desde Configuración"
            Toast.makeText(this, "Ve a Configuración > Bluetooth y empareja tu módulo", Toast.LENGTH_LONG).show()
            return
        }

        txtEstadoBT.text = "Conectando a ${dispositivoEncontrado.name}..."

        Thread {
            try {
                bluetoothSocket = dispositivoEncontrado.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothSocket?.connect()
                isConnected = true
                
                handler.post {
                    txtEstadoBT.text = "Conectado: ${dispositivoEncontrado.name}"
                    statusDot.setBackgroundResource(R.drawable.dot_green)
                    btnConectar.text = "Desconectar"
                    iniciarLectura()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post {
                    txtEstadoBT.text = "Error de conexión"
                    Toast.makeText(this, "No se pudo conectar: ${e.message}", Toast.LENGTH_LONG).show()
                    isConnected = false
                }
            }
        }.start()
    }

    private fun iniciarLectura() {
        readingThread = Thread {
            try {
                val inputStream = bluetoothSocket?.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream))

                while (isConnected) {
                    val linea = reader.readLine()
                    if (linea != null && linea.isNotEmpty()) {
                        handler.post {
                            procesarDatoRecibido(linea)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post {
                    txtEstadoBT.text = "Conexión perdida"
                    btnConectar.text = "Conectar Bluetooth"
                    isConnected = false
                }
            }
        }
        readingThread?.start()
    }

    fun procesarDatoRecibido(dato: String) {
        try {
            val partes = dato.split(",")
            val temperaturaStr = partes[0].trim()
            val temperatura = temperaturaStr.toFloatOrNull()

            if (partes.size > 1) {
                val humedadStr = partes[1].trim()
                val humedad = humedadStr.toFloatOrNull()
                if (humedad != null) {
                    txtHumedad.text = String.format("%.1f%%", humedad)
                }
            }

            if (temperatura == null) {
                txtTemperatura.text = "--"
                txtHumedad.text = "--%"
                txtIconoEstado.text = "⚠️"
                txtEstadoClima.text = "Datos inválidos"
                txtMensajeAlerta.text = "Error de lectura"
                mainLayout.setBackgroundColor(Color.parseColor("#FFF9C4")) // Amarillo suave
                dataCard.setStrokeColor(Color.parseColor("#FFD54F"))
                return
            }

            txtTemperatura.text = String.format("%.1f", temperatura)

            when {
                temperatura < 0 -> {
                    txtIconoEstado.text = "🧊❄️🥶"
                    txtEstadoClima.text = "¡Clima Gélido! (Riesgo de helada)"
                    mainLayout.setBackgroundColor(Color.parseColor("#4FC3F7")) // Celeste Intenso
                    dataCard.setStrokeColor(Color.parseColor("#039BE5"))
                    txtMensajeAlerta.text = ""
                }
                temperatura in 0.0..15.0 -> {
                    txtIconoEstado.text = "❄️🌡️"
                    txtEstadoClima.text = "Clima Frío (Usa abrigo)"
                    mainLayout.setBackgroundColor(Color.parseColor("#64B5F6")) // Azul Vibrante
                    dataCard.setStrokeColor(Color.parseColor("#1E88E5"))
                    txtMensajeAlerta.text = ""
                }
                temperatura in 15.1..25.0 -> {
                    txtIconoEstado.text = "🌤️"
                    txtEstadoClima.text = "Clima Ideal (Agradable)"
                    mainLayout.setBackgroundColor(Color.parseColor("#81C784")) // Verde Intenso
                    dataCard.setStrokeColor(Color.parseColor("#43A047"))
                    txtMensajeAlerta.text = ""
                }
                temperatura in 25.1..34.9 -> {
                    txtIconoEstado.text = "☀️🌡️"
                    txtEstadoClima.text = "Clima Cálido (Mantente hidratado)"
                    mainLayout.setBackgroundColor(Color.parseColor("#FFB74D")) // Naranja Vibrante
                    dataCard.setStrokeColor(Color.parseColor("#FB8C00"))
                    txtMensajeAlerta.text = ""
                }
                temperatura >= 35 -> {
                    txtIconoEstado.text = "🔥🌡️🥵"
                    txtEstadoClima.text = "Calor Intenso"
                    mainLayout.setBackgroundColor(Color.parseColor("#FF5252")) // Rojo Vibrante
                    dataCard.setStrokeColor(Color.parseColor("#D32F2F"))
                    txtMensajeAlerta.text = "¡ALERTA: CALOR EXTREMO!"
                }
            }

        } catch (e: Exception) {
            txtTemperatura.text = "--"
            txtHumedad.text = "--%"
            txtIconoEstado.text = "⚠️"
            txtMensajeAlerta.text = "Error de lectura"
            mainLayout.setBackgroundColor(Color.parseColor("#FFF9C4"))
            dataCard.setStrokeColor(Color.parseColor("#FFD54F"))
        }
    }

    private fun desconectarBluetooth() {
        isConnected = false
        try {
            bluetoothSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        txtEstadoBT.text = "Desconectado"
        statusDot.setBackgroundResource(R.drawable.dot_red)
        btnConectar.text = "Conectar Bluetooth"
        txtTemperatura.text = "--"
        txtHumedad.text = "--%"
        txtIconoEstado.text = "🌡️"
        txtEstadoClima.text = "Desconectado"
        txtMensajeAlerta.text = ""
        mainLayout.setBackgroundResource(R.drawable.bg_main)
        dataCard.setStrokeColor(Color.parseColor("#E1E3E8"))
    }

    override fun onDestroy() {
        super.onDestroy()
        desconectarBluetooth()
    }
}