package com.sigcpa.validacion_02

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemperaturaAppTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun TC01_verificarTemperaturaNormal_22Grados() {
        // Simular que la app recibe "22.0"
        activityRule.scenario.onActivity { activity ->
            activity.procesarDatoRecibido("22.0,50.0")
        }

        // Verificar que muestra "22.0" (El símbolo °C está separado en la UI moderna)
        onView(withId(R.id.txtTemperatura))
            .check(matches(withText("22.0")))

        // Verificar que muestra "50.0%" de humedad
        onView(withId(R.id.txtHumedad))
            .check(matches(withText("50.0%")))

        // Verificar que NO hay mensaje de alerta
        onView(withId(R.id.txtMensajeAlerta))
            .check(matches(withText("")))
    }

    @Test
    fun TC02_verificarAlertaPorCalorExtremo_35Grados() {
        // Simular que la app recibe "35.0"
        activityRule.scenario.onActivity { activity ->
            activity.procesarDatoRecibido("35.0,50.0")
        }

        // Verificar que muestra "35.0"
        onView(withId(R.id.txtTemperatura))
            .check(matches(withText("35.0")))

        // Verificar que aparece mensaje de alerta
        onView(withId(R.id.txtMensajeAlerta))
            .check(matches(withText("¡ALERTA: CALOR EXTREMO!")))
    }

    @Test
    fun TC03_verificarTemperaturaLimite_34Grados() {
        // Simular que la app recibe "34.0" (último valor normal)
        activityRule.scenario.onActivity { activity ->
            activity.procesarDatoRecibido("34.0,50.0")
        }

        // Verificar que muestra "34.0"
        onView(withId(R.id.txtTemperatura))
            .check(matches(withText("34.0")))

        // NO debe haber mensaje de alerta
        onView(withId(R.id.txtMensajeAlerta))
            .check(matches(withText("")))
    }

    @Test
    fun TC04_verificarManejoDeError_DatoCorrupto() {
        // Simular que la app recibe dato corrupto "ERROR"
        activityRule.scenario.onActivity { activity ->
            activity.procesarDatoRecibido("ERROR")
        }

        // Verificar mensaje de error
        onView(withId(R.id.txtMensajeAlerta))
            .check(matches(withText("Error de lectura")))
    }

    @Test
    fun TC05_verificarManejoDeError_CadenaVacia() {
        // Simular que la app recibe cadena vacía
        activityRule.scenario.onActivity { activity ->
            activity.procesarDatoRecibido("")
        }

        // Verificar mensaje de error
        onView(withId(R.id.txtMensajeAlerta))
            .check(matches(withText("Error de lectura")))
    }

    @Test
    fun TC06_verificarTemperaturaConDecimales_26p2Grados() {
        // Simular que la app recibe "26.2"
        activityRule.scenario.onActivity { activity ->
            activity.procesarDatoRecibido("26.2,55.0")
        }

        // Verificar que muestra "26.2"
        onView(withId(R.id.txtTemperatura))
            .check(matches(withText("26.2")))
    }
}
