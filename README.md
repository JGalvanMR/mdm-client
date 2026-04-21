# 📱 **Repositorio: mdm-client**

## 🧠 Descripción General

Aplicación Android (Kotlin) que actúa como **agente MDM en el dispositivo**.
Se comunica con el backend, ejecuta comandos y reporta estado.

---

## 🎯 Propósito

Convertir un dispositivo Android en un nodo administrado remotamente.

---

## 🏗️ Arquitectura

Arquitectura modular:

```
UI → Services → Core → Data → Network
         ↓
     Commands / Workers / Receivers
```

---

## 📁 Estructura

```
/commands     → ejecución de comandos remotos
/core         → lógica central
/data         → modelos/datos
/device       → info del dispositivo
/receiver     → broadcast receivers
/service      → servicios en segundo plano
/ui           → interfaces
/utils        → utilidades
/worker       → tareas programadas
```

---

## 🔄 Flujos clave

### 1. Conexión al servidor

* Usa `SERVER_URL`
* Autenticación con `ADMIN_KEY`
* Establece WebSocket persistente

---

### 2. Recepción de comandos

1. Llega mensaje WS
2. Se interpreta comando
3. Se ejecuta en módulo `/commands`
4. Resultado enviado al backend

---

### 3. Heartbeat / monitoreo

* Envío periódico:

  * batería
  * red
  * ubicación
* Uso de WorkManager

---

### 4. Streaming de pantalla

1. Captura pantalla (MediaProjection)
2. Codifica en H.264
3. Envía por WebSocket
4. Backend redirige al panel

---

## ⚙️ Configuración

En `build.gradle.kts`:

```kotlin
SERVER_URL = "..."
ADMIN_KEY = "..."
HEARTBEAT_INTERVAL = ...
```

---

## 📦 Dependencias

* OkHttp → networking
* Gson → serialización
* WorkManager → tareas
* Play Services Location → ubicación
* Security Crypto → almacenamiento seguro

---

## 🔐 Permisos importantes

* INTERNET
* ACCESS_FINE_LOCATION
* FOREGROUND_SERVICE
* SYSTEM_ALERT_WINDOW (posible)
* DEVICE_ADMIN / DEVICE_OWNER

---

## ⚠️ Observaciones técnicas

* Buen desacoplamiento por módulos
* Riesgo:

  * manejo de lifecycle en servicios
  * reconexión WebSocket
* Oportunidades:

  * usar coroutines más estructuradas
  * retry strategy robusta

---

---
