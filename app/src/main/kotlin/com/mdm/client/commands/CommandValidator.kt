package com.mdm.client.commands

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mdm.client.core.MdmResult
import com.mdm.client.data.models.CommandType

class CommandValidator {

    private val gson = Gson()

    /**
     * Valida que el commandType sea conocido y que los parámetros sean JSON válido si se
     * proporcionan. Retorna Failure con descripción del problema si no es válido.
     */
    fun validate(commandType: String, parametersJson: String?): MdmResult<Unit> {
        // 1. Tipo conocido
        if (commandType !in CommandType.ALL) {
            return MdmResult.Failure(
                    "Tipo de comando desconocido: '$commandType'. " +
                            "Válidos: ${CommandType.ALL.joinToString(", ")}"
            )
        }

        // 2. Si hay parámetros, deben ser JSON válido
        if (!parametersJson.isNullOrBlank()) {
            try {
                JsonParser.parseString(parametersJson)
            } catch (e: Exception) {
                return MdmResult.Failure("Parámetros inválidos (JSON malformado): $parametersJson")
            }
        }

        // 3. Validaciones específicas por tipo
        when (commandType) {
            CommandType.SET_SCREEN_TIMEOUT -> {
                val seconds =
                        parametersJson?.let {
                            try {
                                val obj = JsonParser.parseString(it).asJsonObject
                                obj.get("seconds")?.asInt
                            } catch (e: Exception) {
                                null
                            }
                        }
                if (seconds == null || seconds < 5 || seconds > 3600) {
                    return MdmResult.Failure(
                            "SET_SCREEN_TIMEOUT requiere parámetro {\"seconds\": N} donde N es 5–3600."
                    )
                }
            }
            CommandType.WIPE_DATA -> {
                // Requiere confirmación explícita
                val confirmed =
                        parametersJson?.let {
                            try {
                                val obj = JsonParser.parseString(it).asJsonObject
                                obj.get("confirm")?.asBoolean
                            } catch (e: Exception) {
                                null
                            }
                        }
                if (confirmed != true) {
                    return MdmResult.Failure(
                            "WIPE_DATA requiere parámetro {\"confirm\": true} como medida de seguridad."
                    )
                }
            }
        }

        return MdmResult.Success(Unit)
    }
}
