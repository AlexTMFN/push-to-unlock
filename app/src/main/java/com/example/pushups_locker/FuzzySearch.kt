package com.example.pushups_locker

object FuzzySearch {
    fun matches(query: String, text: String): Boolean {
        if (query.isEmpty()) return true
        val q = query.lowercase()
        val t = text.lowercase()
        
        var queryIndex = 0
        var textIndex = 0
        
        while (queryIndex < q.length && textIndex < t.length) {
            if (q[queryIndex] == t[textIndex]) {
                queryIndex++
            }
            textIndex++
        }
        
        return queryIndex == q.length
    }
}