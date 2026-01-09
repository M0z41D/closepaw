package com.moonkey.androidagent

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sanitizer - Converts AccessibilityNodeInfo tree to a lean JSON list.
 * Mirrors the spirit of sanitizer.py from android-action-kernel.
 */
object Sanitizer {
    
    private const val MAX_ELEMENTS = 80
    private const val MAX_STRING_LENGTH = 60

    /**
     * Node representation for LLM consumption
     */
    data class Element(
        val index: Int,
        val packageName: String,
        val className: String,
        val resourceId: String,
        val text: String,
        val desc: String,
        val clickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean,
        val bounds: IntArray,
        val center: IntArray,
        val node: AccessibilityNodeInfo? = null  // Keep reference for action execution
    )

    /**
     * Snapshot the current screen and return sanitized elements list
     */
    fun snapshot(root: AccessibilityNodeInfo?): List<Element> {
        if (root == null) return emptyList()
        
        val elements = mutableListOf<Element>()
        traverse(root, elements)
        return elements.take(MAX_ELEMENTS)
    }

    /**
     * Convert elements to JSON string for LLM
     */
    fun toJson(elements: List<Element>): String {
        val jsonArray = JSONArray()
        for (elem in elements) {
            val obj = JSONObject().apply {
                put("index", elem.index)
                put("package", elem.packageName)
                put("class", elem.className)
                put("id", elem.resourceId)
                put("text", elem.text)
                put("desc", elem.desc)
                put("clickable", elem.clickable)
                put("editable", elem.editable)
                put("scrollable", elem.scrollable)
                put("bounds", JSONArray(elem.bounds.toList()))
                put("center", JSONArray(elem.center.toList()))
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }

    private fun traverse(node: AccessibilityNodeInfo, elements: MutableList<Element>) {
        if (elements.size >= MAX_ELEMENTS) return

        val text = node.text?.toString()?.take(MAX_STRING_LENGTH) ?: ""
        val desc = node.contentDescription?.toString()?.take(MAX_STRING_LENGTH) ?: ""
        val resourceId = node.viewIdResourceName?.take(MAX_STRING_LENGTH) ?: ""
        val clickable = node.isClickable
        val editable = node.isEditable
        val scrollable = node.isScrollable

        // Keep node if it's interactive or has meaningful text
        val shouldKeep = clickable || editable || scrollable || 
                         text.isNotBlank() || desc.isNotBlank()

        if (shouldKeep) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            val element = Element(
                index = elements.size,
                packageName = node.packageName?.toString() ?: "",
                className = node.className?.toString()?.substringAfterLast('.') ?: "",
                resourceId = resourceId,
                text = text.normalizeWhitespace(),
                desc = desc.normalizeWhitespace(),
                clickable = clickable,
                editable = editable,
                scrollable = scrollable,
                bounds = intArrayOf(bounds.left, bounds.top, bounds.right, bounds.bottom),
                center = intArrayOf(bounds.centerX(), bounds.centerY()),
                node = node
            )
            elements.add(element)
        }

        // Traverse children
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverse(child, elements)
            }
        }
    }

    private fun String.normalizeWhitespace(): String {
        return this.replace(Regex("\\s+"), " ").trim()
    }
}
