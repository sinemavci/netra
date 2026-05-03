package com.netra.library

object NetraClientList {
    private val sourceMap = mutableMapOf<String, NetraClient>()

    fun getClients(): List<NetraClient> {
        return sourceMap.values.toList()
    }

    fun add(client: NetraClient) {
        sourceMap[client.id] = client
    }

    fun remove(sourceId: String) {
        sourceMap.remove(sourceId)
    }
}