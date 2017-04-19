/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.TraceElement
import com.intellij.debugger.streams.ui.LinkedValuesMapping
import com.intellij.debugger.streams.ui.TraceController
import com.intellij.debugger.streams.ui.ValueWithPosition
import com.intellij.debugger.streams.ui.ValuesPositionsListener
import java.awt.Component
import java.awt.GridLayout
import javax.swing.JPanel

/**
 * @author Vitaliy.Bibaev
 */
open class FlatView(controllers: List<TraceController>, evaluationContext: EvaluationContextImpl)
  : JPanel(GridLayout(1, 2 * controllers.size - 1)) {
  private val myPool = mutableMapOf<TraceElement, ValueWithPositionImpl>()

  init {
    var prevMappingPane: MappingPane? = null
    var lastValues: List<ValueWithPositionImpl>? = null
    for ((index, controller) in controllers.subList(0, controllers.size - 1).withIndex()) {
      val (valuesBefore, valuesAfter, mapping) = controller.resolve(controllers[index + 1])
      val mappingPane = MappingPane(controller.call.name, valuesBefore, mapping)

      val view = PositionsAwareCollectionView(" ", evaluationContext, valuesBefore)
      controller.register(view)
      view.addValuesPositionsListener(object : ValuesPositionsListener {
        override fun valuesPositionsChanged() {
          mappingPane.repaint()
        }
      })

      val prevMapping = prevMappingPane
      if (prevMapping != null) {
        view.addValuesPositionsListener(object : ValuesPositionsListener {
          override fun valuesPositionsChanged() {
            prevMapping.repaint()
          }
        })
      }

      add(view)
      add(mappingPane)

      prevMappingPane = mappingPane
      lastValues = valuesAfter
    }

    lastValues?.let {
      val view = PositionsAwareCollectionView(" ", evaluationContext, it)
      controllers.last().register(view)
      view.addValuesPositionsListener(object : ValuesPositionsListener {
        override fun valuesPositionsChanged() {
          prevMappingPane?.repaint()
        }
      })

      add(view)
    }
  }

  final override fun add(component: Component): Component = super.add(component)

  private fun getValue(element: TraceElement): ValueWithPositionImpl = myPool.computeIfAbsent(element, ::ValueWithPositionImpl)

  private fun TraceController.resolve(nextController: TraceController): LinkedValuesWithPositions {
    val prevValues = mutableListOf<ValueWithPositionImpl>()
    val mapping = mutableMapOf<ValueWithPositionImpl, MutableSet<ValueWithPositionImpl>>()

    for (element in resolvedTrace.values) {
      val prevValue = getValue(element)
      prevValues += prevValue
      for (nextElement in resolvedTrace.getNextValues(element)) {
        val nextValue = getValue(nextElement)
        mapping.computeIfAbsent(prevValue, { mutableSetOf() }) += nextValue
        mapping.computeIfAbsent(nextValue, { mutableSetOf() }) += prevValue
      }
    }

    val resultMapping = mutableMapOf<ValueWithPosition, List<ValueWithPosition>>()
    for (key in mapping.keys) {
      resultMapping.put(key, mapping[key]!!.toList())
    }

    return LinkedValuesWithPositions(prevValues, nextController.values.map { getValue(it) }, object : LinkedValuesMapping {
      override fun getLinkedValues(value: ValueWithPosition): List<ValueWithPosition>? {
        return resultMapping[value]
      }
    })
  }

  private data class LinkedValuesWithPositions(
    val valuesBefore: List<ValueWithPositionImpl>,
    val valuesAfter: List<ValueWithPositionImpl>,
    val mapping: LinkedValuesMapping
  )
}