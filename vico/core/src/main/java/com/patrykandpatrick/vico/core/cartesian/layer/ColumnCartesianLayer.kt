/*
 * Copyright 2024 by Patryk Goworowski and Patrick Michalik.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.patrykandpatrick.vico.core.cartesian.layer

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.MutableHorizontalDimensions
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartRanges
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerDrawingModel
import com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.MutableCartesianChartRanges
import com.patrykandpatrick.vico.core.cartesian.data.forEachIn
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.cartesian.marker.MutableColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.common.Defaults
import com.patrykandpatrick.vico.core.common.VerticalPosition
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.data.CartesianLayerDrawingModelInterpolator
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.data.MutableExtraStore
import com.patrykandpatrick.vico.core.common.doubled
import com.patrykandpatrick.vico.core.common.getRepeating
import com.patrykandpatrick.vico.core.common.getStart
import com.patrykandpatrick.vico.core.common.half
import com.patrykandpatrick.vico.core.common.inBounds
import com.patrykandpatrick.vico.core.common.saveLayer
import com.patrykandpatrick.vico.core.common.unaryMinus
import java.util.Objects
import kotlin.math.abs
import kotlin.math.min

/**
 * Displays data as vertical bars.
 *
 * @property columnProvider provides the column [LineComponent]s.
 * @property columnCollectionSpacingDp the spacing between neighboring column collections (in dp).
 * @property mergeMode defines how columns should be drawn in column collections.
 * @property dataLabel the [TextComponent] for the data labels. Use `null` for no data labels.
 * @property dataLabelVerticalPosition the vertical position of each data label relative to its
 *   column’s top edge.
 * @property dataLabelValueFormatter the [CartesianValueFormatter] for the data labels.
 * @property dataLabelRotationDegrees the rotation of the data labels (in degrees).
 * @property rangeProvider defines the _x_ and _y_ ranges.
 * @property verticalAxisPosition the position of the [VerticalAxis] with which the
 *   [ColumnCartesianLayer] should be associated. Use this for independent [CartesianLayer] scaling.
 * @property drawingModelInterpolator interpolates the [ColumnCartesianLayer]’s
 *   [ColumnCartesianLayerDrawingModel]s.
 */
@Stable
public open class ColumnCartesianLayer
protected constructor(
  protected val columnProvider: ColumnProvider,
  protected val columnCollectionSpacingDp: Float = Defaults.COLUMN_COLLECTION_SPACING,
  protected val mergeMode: (ExtraStore) -> MergeMode = { MergeMode.Grouped() },
  protected val dataLabel: TextComponent? = null,
  protected val dataLabelVerticalPosition: VerticalPosition = VerticalPosition.Top,
  protected val dataLabelValueFormatter: CartesianValueFormatter =
    CartesianValueFormatter.decimal(),
  protected val dataLabelRotationDegrees: Float = 0f,
  protected val rangeProvider: CartesianLayerRangeProvider = CartesianLayerRangeProvider.auto(),
  protected val verticalAxisPosition: Axis.Position.Vertical? = null,
  protected val drawingModelInterpolator:
    CartesianLayerDrawingModelInterpolator<
      ColumnCartesianLayerDrawingModel.ColumnInfo,
      ColumnCartesianLayerDrawingModel,
    > =
    CartesianLayerDrawingModelInterpolator.default(),
  protected val drawingModelKey: ExtraStore.Key<ColumnCartesianLayerDrawingModel>,
) : BaseCartesianLayer<ColumnCartesianLayerModel>() {
  private val _markerTargets =
    mutableMapOf<Double, MutableList<MutableColumnCartesianLayerMarkerTarget>>()

  protected val stackInfo: MutableMap<Double, StackInfo> = mutableMapOf()

  /** Holds information on the [ColumnCartesianLayer]’s horizontal dimensions. */
  protected val horizontalDimensions: MutableHorizontalDimensions = MutableHorizontalDimensions()

  override val markerTargets: Map<Double, List<CartesianMarker.Target>> = _markerTargets

  /** Creates a [ColumnCartesianLayer]. */
  public constructor(
    columnProvider: ColumnProvider,
    columnCollectionSpacingDp: Float = Defaults.COLUMN_COLLECTION_SPACING,
    mergeMode: (ExtraStore) -> MergeMode = { MergeMode.Grouped() },
    dataLabel: TextComponent? = null,
    dataLabelVerticalPosition: VerticalPosition = VerticalPosition.Top,
    dataLabelValueFormatter: CartesianValueFormatter = CartesianValueFormatter.decimal(),
    dataLabelRotationDegrees: Float = 0f,
    rangeProvider: CartesianLayerRangeProvider = CartesianLayerRangeProvider.auto(),
    verticalAxisPosition: Axis.Position.Vertical? = null,
    drawingModelInterpolator:
      CartesianLayerDrawingModelInterpolator<
        ColumnCartesianLayerDrawingModel.ColumnInfo,
        ColumnCartesianLayerDrawingModel,
      > =
      CartesianLayerDrawingModelInterpolator.default(),
  ) : this(
    columnProvider,
    columnCollectionSpacingDp,
    mergeMode,
    dataLabel,
    dataLabelVerticalPosition,
    dataLabelValueFormatter,
    dataLabelRotationDegrees,
    rangeProvider,
    verticalAxisPosition,
    drawingModelInterpolator,
    ExtraStore.Key(),
  )

  override fun drawInternal(context: CartesianDrawingContext, model: ColumnCartesianLayerModel) {
    with(context) {
      _markerTargets.clear()
      drawChartInternal(model, ranges, model.extraStore.getOrNull(drawingModelKey))
      stackInfo.clear()
    }
  }

  protected open fun CartesianDrawingContext.drawChartInternal(
    model: ColumnCartesianLayerModel,
    ranges: CartesianChartRanges,
    drawingModel: ColumnCartesianLayerDrawingModel?,
  ) {
    val yRange = ranges.getYRange(verticalAxisPosition)
    val heightMultiplier = layerBounds.height() / yRange.length.toFloat()

    var drawingStart: Float
    var height: Float
    var columnCenterX: Float
    var columnTop: Float
    var columnBottom: Float
    val zeroLinePosition =
      layerBounds.bottom + (yRange.minY / yRange.length).toFloat() * layerBounds.height()
    val mergeMode = mergeMode(model.extraStore)

    canvas.saveLayer(opacity = drawingModel?.opacity ?: 1f)

    model.series.forEachIndexed { index, entryCollection ->
      drawingStart = getDrawingStart(index, model.series.size, mergeMode) - scroll

      entryCollection.forEachIn(ranges.minX..ranges.maxX) { entry, _ ->
        val columnInfo = drawingModel?.getOrNull(index)?.get(entry.x)
        height =
          (columnInfo?.height ?: (abs(entry.y) / yRange.length)).toFloat() * layerBounds.height()
        val xSpacingMultiplier = ((entry.x - ranges.minX) / ranges.xStep).toFloat()
        val column = columnProvider.getColumn(entry, index, model.extraStore)
        columnCenterX =
          drawingStart +
            (horizontalDimensions.xSpacing * xSpacingMultiplier +
              columnProvider
                .getWidestSeriesColumn(index, model.extraStore)
                .thicknessDp
                .half
                .pixels * zoom) * layoutDirectionMultiplier

        when (mergeMode) {
          MergeMode.Stacked -> {
            val stackInfo = stackInfo.getOrPut(entry.x) { StackInfo() }
            columnBottom =
              if (entry.y >= 0) {
                zeroLinePosition - stackInfo.topHeight
              } else {
                zeroLinePosition + stackInfo.bottomHeight + height
              }
            columnTop = (columnBottom - height).coerceAtMost(columnBottom)
            stackInfo.update(entry.y, height)
          }
          is MergeMode.Grouped -> {
            columnBottom = zeroLinePosition + if (entry.y < 0f) height else 0f
            columnTop = columnBottom - height
          }
        }

        val columnSignificantY = if (entry.y < 0f) columnBottom else columnTop

        if (
          column.intersectsVertical(
            context = this,
            top = columnTop,
            bottom = columnBottom,
            centerX = columnCenterX,
            boundingBox = layerBounds,
            thicknessScale = zoom,
          )
        ) {
          updateMarkerTargets(entry, columnCenterX, columnSignificantY, column, mergeMode)
          column.drawVertical(this, columnTop, columnBottom, columnCenterX, zoom)
        }

        if (mergeMode is MergeMode.Grouped) {
          drawDataLabel(
            modelEntriesSize = model.series.size,
            columnThicknessDp = column.thicknessDp,
            dataLabelValue = entry.y,
            x = columnCenterX,
            y = columnSignificantY,
            isFirst = index == 0 && entry.x == ranges.minX,
            isLast = index == model.series.lastIndex && entry.x == ranges.maxX,
            mergeMode = mergeMode,
          )
        } else if (index == model.series.lastIndex) {
          drawStackedDataLabel(
            modelEntriesSize = model.series.size,
            columnThicknessDp = column.thicknessDp,
            stackInfo = stackInfo.getValue(entry.x),
            x = columnCenterX,
            zeroLinePosition = zeroLinePosition,
            heightMultiplier = heightMultiplier,
            isFirst = entry.x == ranges.minX,
            isLast = entry.x == ranges.maxX,
            mergeMode = mergeMode,
          )
        }
      }
    }

    canvas.restore()
  }

  protected open fun CartesianDrawingContext.drawStackedDataLabel(
    modelEntriesSize: Int,
    columnThicknessDp: Float,
    stackInfo: StackInfo,
    x: Float,
    zeroLinePosition: Float,
    heightMultiplier: Float,
    isFirst: Boolean,
    isLast: Boolean,
    mergeMode: MergeMode,
  ) {
    if (stackInfo.topY > 0f) {
      drawDataLabel(
        modelEntriesSize = modelEntriesSize,
        columnThicknessDp = columnThicknessDp,
        dataLabelValue = stackInfo.topY,
        x = x,
        y = zeroLinePosition - stackInfo.topHeight,
        isFirst = isFirst,
        isLast = isLast,
        mergeMode = mergeMode,
      )
    }
    if (stackInfo.bottomY < 0f) {
      drawDataLabel(
        modelEntriesSize = modelEntriesSize,
        columnThicknessDp = columnThicknessDp,
        dataLabelValue = stackInfo.bottomY,
        x = x,
        y = zeroLinePosition + stackInfo.bottomHeight,
        isFirst = isFirst,
        isLast = isLast,
        mergeMode = mergeMode,
      )
    }
  }

  protected open fun CartesianDrawingContext.drawDataLabel(
    modelEntriesSize: Int,
    columnThicknessDp: Float,
    dataLabelValue: Double,
    x: Float,
    y: Float,
    isFirst: Boolean,
    isLast: Boolean,
    mergeMode: MergeMode,
  ) {
    dataLabel?.let { textComponent ->
      val canUseXSpacing =
        mergeMode == MergeMode.Stacked || mergeMode is MergeMode.Grouped && modelEntriesSize == 1
      var maxWidth =
        when {
          canUseXSpacing -> horizontalDimensions.xSpacing
          mergeMode is MergeMode.Grouped ->
            (columnThicknessDp + min(columnCollectionSpacingDp, mergeMode.columnSpacingDp).half)
              .pixels * zoom
          else -> error(message = "Encountered an unexpected `MergeMode`.")
        }
      if (isFirst) maxWidth = maxWidth.coerceAtMost(horizontalDimensions.startPadding.doubled)
      if (isLast) maxWidth = maxWidth.coerceAtMost(horizontalDimensions.endPadding.doubled)
      val text = dataLabelValueFormatter.format(this, dataLabelValue, verticalAxisPosition)
      val dataLabelWidth =
        textComponent
          .getWidth(context = this, text = text, rotationDegrees = dataLabelRotationDegrees)
          .coerceAtMost(maximumValue = maxWidth)

      if (
        x - dataLabelWidth.half > layerBounds.right || x + dataLabelWidth.half < layerBounds.left
      ) {
        return
      }

      val labelVerticalPosition =
        if (dataLabelValue < 0f) -dataLabelVerticalPosition else dataLabelVerticalPosition

      val verticalPosition =
        labelVerticalPosition.inBounds(
          y = y,
          bounds = layerBounds,
          componentHeight =
            textComponent.getHeight(
              context = this,
              text = text,
              maxWidth = maxWidth.toInt(),
              rotationDegrees = dataLabelRotationDegrees,
            ),
        )
      textComponent.draw(
        context = this,
        text = text,
        x = x,
        y = y,
        verticalPosition = verticalPosition,
        maxWidth = maxWidth.toInt(),
        rotationDegrees = dataLabelRotationDegrees,
      )
    }
  }

  protected open fun CartesianDrawingContext.updateMarkerTargets(
    entry: ColumnCartesianLayerModel.Entry,
    canvasX: Float,
    canvasY: Float,
    column: LineComponent,
    mergeMode: MergeMode,
  ) {
    if (canvasX <= layerBounds.left - 1 || canvasX >= layerBounds.right + 1) return
    val targetColumn =
      ColumnCartesianLayerMarkerTarget.Column(
        entry,
        canvasY.coerceIn(layerBounds.top, layerBounds.bottom),
        column.solidOrStrokeColor,
      )
    when (mergeMode) {
      is MergeMode.Grouped ->
        _markerTargets.getOrPut(entry.x) { mutableListOf() } +=
          MutableColumnCartesianLayerMarkerTarget(entry.x, canvasX, mutableListOf(targetColumn))
      MergeMode.Stacked ->
        _markerTargets
          .getOrPut(entry.x) {
            mutableListOf(MutableColumnCartesianLayerMarkerTarget(entry.x, canvasX))
          }
          .first()
          .columns +=
          ColumnCartesianLayerMarkerTarget.Column(
            entry,
            canvasY.coerceIn(layerBounds.top, layerBounds.bottom),
            column.solidOrStrokeColor,
          )
    }
  }

  override fun updateRanges(ranges: MutableCartesianChartRanges, model: ColumnCartesianLayerModel) {
    val mergeMode = mergeMode(model.extraStore)
    val minY = mergeMode.getMinY(model)
    val maxY = mergeMode.getMaxY(model)
    ranges.tryUpdate(
      rangeProvider.getMinX(model.minX, model.maxX, model.extraStore),
      rangeProvider.getMaxX(model.minX, model.maxX, model.extraStore),
      rangeProvider.getMinY(minY, maxY, model.extraStore),
      rangeProvider.getMaxY(minY, maxY, model.extraStore),
      verticalAxisPosition,
    )
  }

  override fun updateHorizontalDimensions(
    context: CartesianMeasuringContext,
    horizontalDimensions: MutableHorizontalDimensions,
    model: ColumnCartesianLayerModel,
  ) {
    with(context) {
      val columnCollectionWidth =
        getColumnCollectionWidth(
          if (model.series.isNotEmpty()) model.series.size else 1,
          mergeMode(model.extraStore),
        )
      val xSpacing = columnCollectionWidth + columnCollectionSpacingDp.pixels
      horizontalDimensions.ensureValuesAtLeast(
        xSpacing = xSpacing,
        scalableStartPadding = columnCollectionWidth.half + layerPadding.scalableStartDp.pixels,
        scalableEndPadding = columnCollectionWidth.half + layerPadding.scalableEndDp.pixels,
        unscalableStartPadding = layerPadding.unscalableStartDp.pixels,
        unscalableEndPadding = layerPadding.unscalableEndDp.pixels,
      )
    }
  }

  protected open fun CartesianMeasuringContext.getColumnCollectionWidth(
    entryCollectionSize: Int,
    mergeMode: MergeMode,
  ): Float =
    when (mergeMode) {
      is MergeMode.Stacked ->
        (0..<entryCollectionSize)
          .maxOf { seriesIndex ->
            columnProvider.getWidestSeriesColumn(seriesIndex, model.extraStore).thicknessDp
          }
          .pixels
      is MergeMode.Grouped ->
        getCumulatedThickness(entryCollectionSize) +
          mergeMode.columnSpacingDp.pixels * (entryCollectionSize - 1)
    }

  protected open fun CartesianDrawingContext.getDrawingStart(
    entryCollectionIndex: Int,
    entryCollectionCount: Int,
    mergeMode: MergeMode,
  ): Float {
    val mergeModeComponent =
      when (mergeMode) {
        is MergeMode.Grouped ->
          getCumulatedThickness(entryCollectionIndex) +
            mergeMode.columnSpacingDp.pixels * entryCollectionIndex
        MergeMode.Stacked -> 0f
      }
    return layerBounds.getStart(isLtr) +
      (horizontalDimensions.startPadding +
        (mergeModeComponent - getColumnCollectionWidth(entryCollectionCount, mergeMode).half) *
          zoom) * layoutDirectionMultiplier
  }

  protected open fun CartesianMeasuringContext.getCumulatedThickness(count: Int): Float {
    var thickness = 0f
    for (seriesIndex in 0..<count) {
      thickness += columnProvider.getWidestSeriesColumn(seriesIndex, model.extraStore).thicknessDp
    }
    return thickness.pixels
  }

  /** Defines how a [ColumnCartesianLayer] should draw columns in column collections. */
  @Immutable
  public sealed interface MergeMode {
    /** Returns the minimum _y_ value. */
    public fun getMinY(model: ColumnCartesianLayerModel): Double

    /** Returns the maximum _y_ value. */
    public fun getMaxY(model: ColumnCartesianLayerModel): Double

    /**
     * Groups columns with matching _x_ values horizontally, positioning them [columnSpacingDp] dp
     * apart.
     */
    public class Grouped(internal val columnSpacingDp: Float = Defaults.GROUPED_COLUMN_SPACING) :
      MergeMode {
      override fun getMinY(model: ColumnCartesianLayerModel): Double = model.minY

      override fun getMaxY(model: ColumnCartesianLayerModel): Double = model.maxY

      override fun equals(other: Any?): Boolean =
        this === other || other is Grouped && columnSpacingDp == other.columnSpacingDp

      override fun hashCode(): Int = columnSpacingDp.hashCode()
    }

    /** Stacks columns with matching _x_ values. */
    public data object Stacked : MergeMode {
      override fun getMinY(model: ColumnCartesianLayerModel): Double = model.minAggregateY

      override fun getMaxY(model: ColumnCartesianLayerModel): Double = model.maxAggregateY
    }

    /** Provides access to [MergeMode] factory functions. */
    public companion object
  }

  override fun prepareForTransformation(
    model: ColumnCartesianLayerModel?,
    ranges: CartesianChartRanges,
    extraStore: MutableExtraStore,
  ) {
    drawingModelInterpolator.setModels(
      old = extraStore.getOrNull(drawingModelKey),
      new = model?.toDrawingModel(ranges),
    )
  }

  override suspend fun transform(extraStore: MutableExtraStore, fraction: Float) {
    drawingModelInterpolator.transform(fraction)?.let { extraStore[drawingModelKey] = it }
      ?: extraStore.remove(drawingModelKey)
  }

  private fun ColumnCartesianLayerModel.toDrawingModel(ranges: CartesianChartRanges) =
    series
      .map { series ->
        series.associate { entry ->
          entry.x to
            ColumnCartesianLayerDrawingModel.ColumnInfo(
              height = (abs(entry.y) / ranges.getYRange(verticalAxisPosition).length).toFloat()
            )
        }
      }
      .let(::ColumnCartesianLayerDrawingModel)

  /** Creates a new [ColumnCartesianLayer] based on this one. */
  public fun copy(
    columnProvider: ColumnProvider = this.columnProvider,
    columnCollectionSpacingDp: Float = this.columnCollectionSpacingDp,
    mergeMode: (ExtraStore) -> MergeMode = this.mergeMode,
    dataLabel: TextComponent? = this.dataLabel,
    dataLabelVerticalPosition: VerticalPosition = this.dataLabelVerticalPosition,
    dataLabelValueFormatter: CartesianValueFormatter = this.dataLabelValueFormatter,
    dataLabelRotationDegrees: Float = this.dataLabelRotationDegrees,
    rangeProvider: CartesianLayerRangeProvider = this.rangeProvider,
    verticalAxisPosition: Axis.Position.Vertical? = this.verticalAxisPosition,
    drawingModelInterpolator:
      CartesianLayerDrawingModelInterpolator<
        ColumnCartesianLayerDrawingModel.ColumnInfo,
        ColumnCartesianLayerDrawingModel,
      > =
      this.drawingModelInterpolator,
  ): ColumnCartesianLayer =
    ColumnCartesianLayer(
      columnProvider,
      columnCollectionSpacingDp,
      mergeMode,
      dataLabel,
      dataLabelVerticalPosition,
      dataLabelValueFormatter,
      dataLabelRotationDegrees,
      rangeProvider,
      verticalAxisPosition,
      drawingModelInterpolator,
      drawingModelKey,
    )

  override fun equals(other: Any?): Boolean =
    this === other ||
      other is ColumnCartesianLayer &&
        columnProvider == other.columnProvider &&
        columnCollectionSpacingDp == other.columnCollectionSpacingDp &&
        mergeMode == other.mergeMode &&
        dataLabel == other.dataLabel &&
        dataLabelVerticalPosition == other.dataLabelVerticalPosition &&
        dataLabelValueFormatter == other.dataLabelValueFormatter &&
        dataLabelRotationDegrees == other.dataLabelRotationDegrees &&
        rangeProvider == other.rangeProvider &&
        verticalAxisPosition == other.verticalAxisPosition &&
        drawingModelInterpolator == other.drawingModelInterpolator

  override fun hashCode(): Int =
    Objects.hash(
      columnProvider,
      columnCollectionSpacingDp,
      mergeMode,
      dataLabel,
      dataLabelVerticalPosition,
      dataLabelValueFormatter,
      dataLabelRotationDegrees,
      rangeProvider,
      verticalAxisPosition,
      drawingModelInterpolator,
    )

  protected data class StackInfo(
    var topY: Double = 0.0,
    var bottomY: Double = 0.0,
    var topHeight: Float = 0f,
    var bottomHeight: Float = 0f,
  ) {
    public fun update(y: Double, height: Float) {
      if (y >= 0f) {
        topY += y
        topHeight += height
      } else {
        bottomY += y
        bottomHeight += height
      }
    }
  }

  /** Provides column [LineComponent]s to [ColumnCartesianLayer]s. */
  @Immutable
  public interface ColumnProvider {
    /** Returns the [LineComponent] for the column with the given properties. */
    public fun getColumn(
      entry: ColumnCartesianLayerModel.Entry,
      seriesIndex: Int,
      extraStore: ExtraStore,
    ): LineComponent

    /** Returns the widest column [LineComponent] for the specified series. */
    public fun getWidestSeriesColumn(seriesIndex: Int, extraStore: ExtraStore): LineComponent

    /** Houses [ColumnProvider] factory functions. */
    public companion object {
      private data class Series(private val columns: List<LineComponent>) : ColumnProvider {
        override fun getColumn(
          entry: ColumnCartesianLayerModel.Entry,
          seriesIndex: Int,
          extraStore: ExtraStore,
        ) = columns.getRepeating(seriesIndex)

        override fun getWidestSeriesColumn(seriesIndex: Int, extraStore: ExtraStore) =
          columns.getRepeating(seriesIndex)
      }

      /**
       * Uses one [LineComponent] per series. The [LineComponent]s and series are associated by
       * index. If there are more series than [LineComponent]s, [columns] is iterated multiple
       * times.
       */
      public fun series(columns: List<LineComponent>): ColumnProvider = Series(columns)

      /**
       * Uses one [LineComponent] per series. The [LineComponent]s and series are associated by
       * index. If there are more series than [LineComponent]s, the [LineComponent] list is iterated
       * multiple times.
       */
      public fun series(vararg columns: LineComponent): ColumnProvider = series(columns.toList())
    }
  }
}
