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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // Componentes UI
    private lateinit var txtEstadoBT: TextView
    private lateinit var txtTemperatura: TextView
    private lateinit var txtMensajeAlerta: TextView
    private lateinit var btnConectar: Button
    private lateinit var mainLayout: LinearLayout

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
        txtMensajeAlerta = findViewById(R.id.txtMensajeAlerta)
        btnConectar = findViewById(R.id.btnConectar)
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
                    txtEstadoBT.text = "Conectado a ${dispositivoEncontrado.name}"
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

    private fun procesarDatoRecibido(dato: String) {
        // El Arduino envía formato: "temperatura,humedad" -> Ejemplo: "33.50,55.20"
        try {
            val partes = dato.split(",")
            val temperaturaStr = partes[0].trim()
            val temperatura = temperaturaStr.toFloatOrNull()

            if (temperatura == null) {
                // REQ-04: Manejo de errores (dato corrupto)
                txtTemperatura.text = "--°C"
                txtMensajeAlerta.text = "Error de lectura"
                mainLayout.setBackgroundColor(Color.parseColor("#FFFF00")) // Amarillo
                return
            }

            // Mostrar temperatura con formato
            txtTemperatura.text = String.format("%.1f°C", temperatura)

            // REQ-02: Rango Normal (0 a 34°C)
            if (temperatura <= 34) {
                mainLayout.setBackgroundColor(Color.parseColor("#00FF00")) // Verde
                txtMensajeAlerta.text = ""
            }
            // REQ-03: Alerta por Calor Extremo (≥ 35°C)
            else if (temperatura >= 35) {
                mainLayout.setBackgroundColor(Color.parseColor("#FF0000")) // Rojo
                txtMensajeAlerta.text = "¡ALERTA: CALOR EXTREMO!"
            }

        } catch (e: Exception) {
            // REQ-04: Manejo de excepciones
            txtTemperatura.text = "--°C"
            txtMensajeAlerta.text = "Error de lectura"
            mainLayout.setBackgroundColor(Color.parseColor("#FFFF00")) // Amarillo
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
        btnConectar.text = "Conectar Bluetooth"
        txtTemperatura.text = "--°C"
        txtMensajeAlerta.text = ""
        mainLayout.setBackgroundColor(Color.parseColor("#FFFFFF"))
    }

    override fun onDestroy() {
        super.onDestroy()
        desconectarBluetooth()
    }
}