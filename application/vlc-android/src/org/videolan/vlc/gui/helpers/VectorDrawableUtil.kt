/*
 * ************************************************************************
 *  VectorDrawableUtil.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.gui.helpers;

import android.content.Context
import android.content.res.Resources.NotFoundException
import android.util.Log
import android.util.TypedValue
import androidx.annotation.XmlRes
import androidx.core.content.ContextCompat
import org.videolan.tools.isXmlResource
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicInteger

/**
 * Utility class for converting Android Vector Drawables to SVG format.
 * Handles translation of paths, groups, clip paths, and gradients.
 */
object VectorDrawableUtil {
    private const val TAG = "VLC/VectorDrawableUtil"

    /**
     * Converts an Android Vector Drawable resource to SVG format.
     *
     * @param context Android context used for resource resolution
     * @param newWidth Desired width of the output SVG in pixels
     * @param drawableId Resource ID of the Vector Drawable to convert
     * @param resourceName String identifier of the drawable resource
     * @return String SVG XML
     * @throws NotFoundException if the resource cannot be found or parsed
     */
    fun convertToSvg(context: Context, newWidth: Int, drawableId: Int, resourceName: String): String {
        val res = context.resources
        val clipPathCount = AtomicInteger()
        val gradientCount = AtomicInteger()
        val groupDeque = ArrayDeque<Group>().apply {
            addLast(Group())
        }
        var width = 24f
        var height = 24f
        var viewBox: String? = null
        var opacity: String? = null

        /**
         * Gets an XML resource parser for the drawable, attempting to load via Resources,
         * with fallback to direct asset lookup if the drawable ID points to a non-XML resource.
         */
        val xmlResource = try {
            when {
                res.isXmlResource(drawableId) -> res.getXml(drawableId)
                else -> {
                    val fileName = resourceName.filter { c -> c in 'a' .. 'z' || c in '0'..'9' || c == '_' }
                    context.assets.openXmlResourceParser("res/drawable/$fileName.xml")
                }
            }
        } catch (e: IOException) {
            throw NotFoundException("Resource not found. Drawable ID: $drawableId. Resource Name: $resourceName")
        }

        // First pass - collect all data without writing
        xmlResource.use { parser ->
            var event = parser.eventType
            val innerDepth = parser.depth + 1
            while (event != XmlPullParser.END_DOCUMENT && (parser.depth >= innerDepth || event != XmlPullParser.END_TAG)) {
                val activeGroup = groupDeque.last()
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            VECTOR -> {
                                width = parser.getAttr("width")?.removeSuffix("dip")?.toFloatOrNull() ?: width
                                height = parser.getAttr("height")?.removeSuffix("dip")?.toFloatOrNull() ?: height
                                opacity = parser.getAttr("alpha")
                                val vw = parser.getAttr("viewportWidth")?.toFloatOrNull() ?: 0f
                                val vh = parser.getAttr("viewportHeight")?.toFloatOrNull() ?: 0f
                                viewBox = "0 0 $vw $vh"
                            }

                            GROUP -> {
                                val group = Group()
                                activeGroup.children += group
                                groupDeque.addLast(group)
                            }

                            CLIP_PATH -> {
                                val pathName = "clipPath${clipPathCount.incrementAndGet()}"
                                val clipPath = ClipPath().apply {
                                    name = pathName
                                    pathData = parser.getAttr("pathData")
                                }
                                activeGroup.clipPath = "url(#${pathName})"
                                activeGroup.children += clipPath
                            }

                            PATH -> {
                                val shapePath = ShapePath().apply {
                                    // Need to add clip path
                                    pathData = parser.getAttr("pathData")
                                    strokeWidth = parser.getAttr("strokeWidth")
                                    strokeAlpha = parser.getAttr("strokeAlpha")?.toFloatOrNull()
                                    strokeColor = resolveColor(context, activeGroup, gradientCount, parser.getAttr("strokeColor"))
                                    fillColor = resolveColor(context, activeGroup, gradientCount, parser.getAttr("fillColor"))
                                    fillAlpha = parser.getAttr("fillAlpha")?.toFloatOrNull()
                                    strokeLineCap = lookupStrokeLineCap(parser.getAttr("strokeLineCap"))
                                    strokeLineJoin = lookupStrokeLineJoin(parser.getAttr("strokeLineJoin"))
                                    strokeMiterLimit = parser.getAttr("strokeMiterLimit")?.toFloatOrNull()
                                }
                                activeGroup.children += shapePath
                            }

                            else -> throw NotFoundException("Unhandled tag type: ${parser.name}")
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            GROUP -> groupDeque.removeLastOrNull()
                        }
                    }
                }
                event = parser.next()
            }
        }

        // Second pass - write everything in correct order
        return StringWriter().use { writer ->
            XmlPullParserFactory.newInstance().newSerializer().apply {
                setOutput(writer)
                // Start SVG
                tag("svg") {
                    // Enable pretty printing after writing the root tag. Avoids an empty line from the omitted XML header.
                    setFeature(INDENT_OUTPUT, true)
                    setAttr("xmlns", SVG_NS)
                    setAttr("width", newWidth)
                    setAttr("height", (newWidth * (height.coerceAtLeast(1f) / width.coerceAtLeast(1f))).toInt())
                    setAttr("opacity", opacity)
                    setAttr("viewBox", viewBox)

                    // Write defs if needed
                    val rootGroup = groupDeque.first()
                    if (clipPathCount.get() > 0 || gradientCount.get() > 0) {
                        tag("defs") {
                            processNodes(this, rootGroup.children, true)
                        }
                    }
                    processNodes(this, rootGroup.children)
                }
                endDocument()
            }
            writer.toString()
        }
    }

    private fun processNodes(writer: XmlSerializer, nodes: List<Node>, writeDefs: Boolean = false) {
        writer.apply {
            for (node in nodes) {
                if (writeDefs) {
                    when (node) {
                        is Group -> {
                            if (node.children.isNotEmpty()) {
                                processNodes(writer, node.children, true)
                            }
                        }

                        is ClipPath -> {
                            tag("clipPath") {
                                setAttr("id", node.name)
                                tag("path") {
                                    setAttr("d", node.pathData)
                                }
                            }
                        }

                        is LinearGradient -> {
                            tag("linearGradient") {
                                setAttr("id", node.name)
                                setAttr("x1", node.startX)
                                setAttr("y1", node.startY)
                                setAttr("x2", node.endX)
                                setAttr("y2", node.endY)
                                // SVG defaults to objectBoundingBox
                                setAttr("gradientUnits", "userSpaceOnUse")
                                for (stop in node.children) {
                                    if (stop is GradientStop) {
                                        tag("stop") {
                                            setAttr("offset", stop.offset)
                                            setAttr("stop-color", stop.stopColor)
                                        }
                                    }
                                }
                            }
                        }

                        is RadialGradient -> {
                            tag("radialGradient") {
                                setAttr("id", node.name)
                                setAttr("cx", node.centerX)
                                setAttr("cy", node.centerY)
                                setAttr("r", node.radius)
                                // SVG defaults to objectBoundingBox
                                setAttr("gradientUnits", "userSpaceOnUse")
                                for (stop in node.children) {
                                    if (stop is GradientStop) {
                                        tag("stop") {
                                            setAttr("offset", stop.offset)
                                            setAttr("stop-color", stop.stopColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    when (node) {
                        is Group -> {
                            if (node.children.isNotEmpty()) {
                                tag("g") {
                                    setAttr("id", node.name)
                                    setAttr("clip-path", node.clipPath)
                                    processNodes(writer, node.children)
                                }
                            }
                        }

                        is ShapePath -> {
                            tag("path") {
                                setAttr("id", node.name)
                                setAttr("clip-path", node.clipPath)
                                setAttr("fill", node.fillColor.takeUnless { it == "#00000000" } ?: "none")
                                setAttr("stroke", node.strokeColor.takeIf { it != "#00000000" })
                                // If equal use a single opacity value
                                if (node.strokeAlpha == node.fillAlpha) {
                                    setAttr("opacity", node.fillAlpha)
                                } else {
                                    setAttr("fill-opacity", node.fillAlpha)
                                    setAttr("stroke-opacity", node.strokeAlpha)
                                }
                                setAttr("stroke-width", node.strokeWidth.takeIf { it != "1.0" })
                                setAttr("stroke-linecap", node.strokeLineCap)
                                setAttr("stroke-linejoin", node.strokeLineJoin)
                                setAttr("stroke-miterlimit", node.strokeMiterLimit)
                                setAttr("d", node.pathData)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun processGradient(context: Context, activeGroup: Group, gradientCount: AtomicInteger, @XmlRes res: Int): String? {
        var activeGradient: Gradient? = null
        context.resources.getXml(res).use { parser ->
            var event = parser.eventType
            val innerDepth = parser.depth + 1
            while (event != XmlPullParser.END_DOCUMENT && (parser.depth >= innerDepth || event != XmlPullParser.END_TAG)) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            GRADIENT -> {
                                when (parser.getAttr("type")) {
                                    LINEAR_TYPE -> {
                                        val linear = LinearGradient().apply {
                                            name = "gradient${gradientCount.incrementAndGet()}"
                                            startX = parser.getAttr("startX")?.toFloatOrNull()
                                            startY = parser.getAttr("startY")?.toFloatOrNull()
                                            endX = parser.getAttr("endX")?.toFloatOrNull()
                                            endY = parser.getAttr("endY")?.toFloatOrNull()
                                        }
                                        activeGradient = linear
                                        activeGroup.children += linear
                                    }

                                    RADIAL_TYPE -> {
                                        val radial = RadialGradient().apply {
                                            name = "gradient${gradientCount.incrementAndGet()}"
                                            centerX = parser.getAttr("centerX")?.toFloatOrNull()
                                            centerY = parser.getAttr("centerY")?.toFloatOrNull()
                                            radius = parser.getAttr("gradientRadius")?.toFloatOrNull()
                                        }
                                        activeGradient = radial
                                        activeGroup.children += radial
                                    }
                                }

                            }

                            ITEM -> {
                                val stop = GradientStop().apply {
                                    offset = parser.getAttr("offset")?.toFloatOrNull()
                                    stopColor = resolveColor(context, activeGroup, gradientCount, parser.getAttr("color"))
                                }
                                activeGradient?.let { it.children += stop }
                            }

                            else -> throw NotFoundException("Unhandled tag type: ${parser.name}")
                        }
                    }
                }
                event = parser.next()
            }
        }
        return "url(#${activeGradient?.name})"
    }

    private fun resolveColor(context: Context, activeGroup: Group, gradientCount: AtomicInteger, color: String?): String? {
        if (color.isNullOrEmpty()) return null

        val cInt = when (color.first()) {
            '#' -> color.substring(1).toLong(16).toInt()
            '@', '?' -> color.substring(1).toInt()
            else -> color.toLong(16).toInt()
        }

        val res = context.resources
        return when (color.first()) {
            '@' -> {
                when {
                    res.isXmlResource(cInt) -> processGradient(context, activeGroup, gradientCount, cInt)
                    else -> {
                        try {
                            ContextCompat.getColor(context, cInt).toRGBAHexString()
                        } catch (e: NotFoundException) {
                            Log.d(TAG, "Could not find color resource: $cInt")
                            throw e
                        }
                    }
                }
            }

            '?' -> {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(cInt, typedValue, true)
                if (typedValue.resourceId != 0) {
                    ContextCompat.getColor(context, typedValue.resourceId).toRGBAHexString()
                } else {
                    typedValue.data.toRGBAHexString()
                }
            }

            else -> cInt.toRGBAHexString()
        }
    }

    /**
     * Converts numeric stroke line cap to SVG string.
     * Zero and null map to SVG default "butt" cap.
     */
    private fun lookupStrokeLineCap(key: String?): String? {
        return when (key?.toInt() ?: 0) {
            1 -> "round"
            2 -> "square"
            else -> null
        }
    }

    /**
     * Converts numeric stroke line join to SVG string.
     * Zero and null map to SVG default "miter" join.
     */
    private fun lookupStrokeLineJoin(key: String?): String? {
        return when (key?.toInt() ?: 0) {
            1 -> "round"
            2 -> "bevel"
            else -> null
        }
    }

    private inline fun XmlSerializer.tag(tagName: String, namespace: String = "", block: XmlSerializer.() -> Unit = {}) {
        startTag(namespace, tagName)
        block()
        endTag(namespace, tagName)
    }

    private inline fun XmlSerializer.setAttr(name: String, value: Any?) = value?.let { attribute("", name, it.toString()) }

    private inline fun XmlPullParser.getAttr(name: String): String? = getAttributeValue(ANDROID_NS, name)

    /**
     * Converts ARGB int to RGBA hex string.
     */
    private fun Int.toRGBAHexString(): String {
        val rgba = (this shl 8) or ((this shr 24) and 0xFF)
        return String.format("#%08X", rgba)
    }

    /* Vector Drawable Elements */
    private const val ITEM = "item"
    private const val PATH = "path"
    private const val GROUP = "group"
    private const val VECTOR = "vector"
    private const val LINEAR_TYPE = "0"
    private const val RADIAL_TYPE = "1"
    private const val GRADIENT = "gradient"
    private const val CLIP_PATH = "clip-path"

    /* XML Constants */
    private const val SVG_NS = "http://www.w3.org/2000/svg"
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val INDENT_OUTPUT = "http://xmlpull.org/v1/doc/features.html#indent-output"

    abstract class Node {
        var name: String? = null
        val children = mutableListOf<Node>()
    }

    class Group : Node() {
        var clipPath: String? = null
    }

    abstract class Path : Node() {
        var pathData: String? = null
    }

    class ClipPath : Path()

    class ShapePath : Path() {
        var clipPath: String? = null
        var fillAlpha: Float? = null
        var fillColor: String? = null
        var strokeWidth: String? = null
        var strokeColor: String? = null
        var strokeAlpha: Float? = null
        var strokeLineCap: String? = null
        var strokeLineJoin: String? = null
        var strokeMiterLimit: Float? = null
    }

    abstract class Gradient : Node()

    class LinearGradient : Gradient() {
        var startX: Float? = null
        var startY: Float? = null
        var endX: Float? = null
        var endY: Float? = null
    }

    class RadialGradient : Gradient() {
        var centerX: Float? = null
        var centerY: Float? = null
        var radius: Float? = null
    }

    class GradientStop : Node() {
        var offset: Float? = null
        var stopColor: String? = null
    }
}
