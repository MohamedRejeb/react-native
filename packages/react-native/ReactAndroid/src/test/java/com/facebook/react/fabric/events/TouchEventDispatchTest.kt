/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.fabric.events

import android.util.DisplayMetrics
import android.view.MotionEvent
import com.facebook.react.bridge.*
import com.facebook.react.fabric.FabricUIManager
import com.facebook.react.uimanager.DisplayMetricsHolder
import com.facebook.react.uimanager.events.TouchEvent
import com.facebook.react.uimanager.events.TouchEventCoalescingKeyHelper
import com.facebook.react.uimanager.events.TouchEventType
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.powermock.api.mockito.PowerMockito.mockStatic
import org.powermock.api.mockito.PowerMockito.`when` as whenever
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor
import org.powermock.modules.junit4.rule.PowerMockRule
import org.robolectric.RobolectricTestRunner

@PrepareForTest(Arguments::class, FabricUIManager::class)
@SuppressStaticInitializationFor("com.facebook.react.fabric.FabricUIManager")
@RunWith(RobolectricTestRunner::class)
@PowerMockIgnore("org.mockito.*", "org.robolectric.*", "androidx.*", "android.*")
class TouchEventDispatchTest {
  @get:Rule var rule = PowerMockRule()
  private val touchEventCoalescingKeyHelper = TouchEventCoalescingKeyHelper()

  /** Events (1 pointer): START -> MOVE -> MOVE -> UP */
  private val startMoveEndSequence =
    listOf(
      createTouchEvent(
        gestureTime = GESTURE_START_TIME,
        action = MotionEvent.ACTION_DOWN,
        pointerId = 0,
        pointerIds = intArrayOf(0),
        pointerCoords = arrayOf(pointerCoords(1f, 1f))
      ),
      createTouchEvent(
        gestureTime = GESTURE_START_TIME,
        action = MotionEvent.ACTION_MOVE,
        pointerId = 0,
        pointerIds = intArrayOf(0),
        pointerCoords = arrayOf(pointerCoords(1f, 2f))
      ),
      createTouchEvent(
        gestureTime = GESTURE_START_TIME,
        action = MotionEvent.ACTION_MOVE,
        pointerId = 0,
        pointerIds = intArrayOf(0),
        pointerCoords = arrayOf(pointerCoords(1f, 3f))
      ),
      createTouchEvent(
        gestureTime = GESTURE_START_TIME,
        action = MotionEvent.ACTION_UP,
        pointerId = 0,
        pointerIds = intArrayOf(0),
        pointerCoords = arrayOf(pointerCoords(1f, 3f))
      )
    )

  /** Expected values for [startMoveEndSequence] */
  private val startMoveEndExpectedSequence =
    listOf(
      /*
       * START event for touch 1:
       * {
       *   touches: [touch1],
       *   changed: [touch1]
       * }
       */
      buildGestureEvent(
        surfaceId = SURFACE_ID,
        viewTag = TARGET_VIEW_ID,
        locationX = 1f,
        locationY = 1f,
        time = GESTURE_START_TIME,
        pointerId = 0,
        touches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 1f, GESTURE_START_TIME, 0)),
        changedTouches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 1f, GESTURE_START_TIME, 0))
      ),
      /*
       * MOVE event for touch 1:
       * {
       *   touches: [touch1],
       *   changed: [touch1]
       * }
       */
      buildGestureEvent(
        surfaceId = SURFACE_ID,
        viewTag = TARGET_VIEW_ID,
        locationX = 1f,
        locationY = 2f,
        time = GESTURE_START_TIME,
        pointerId = 0,
        touches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 2f, GESTURE_START_TIME, 0)),
        changedTouches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 2f, GESTURE_START_TIME, 0))
      ),
      /*
       * MOVE event for touch 1:
       * {
       *   touches: [touch1],
       *   changed: [touch1]
       * }
       */
      buildGestureEvent(
        surfaceId = SURFACE_ID,
        viewTag = TARGET_VIEW_ID,
        locationX = 1f,
        locationY = 3f,
        time = GESTURE_START_TIME,
        pointerId = 0,
        touches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 3f, GESTURE_START_TIME, 0)),
        changedTouches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 3f, GESTURE_START_TIME, 0))
      ),
      /*
       * END event for touch 1:
       * {
       *   touches: [],
       *   changed: [touch1]
       * }
       */
      buildGestureEvent(
        surfaceId = SURFACE_ID,
        viewTag = TARGET_VIEW_ID,
        locationX = 1f,
        locationY = 3f,
        time = GESTURE_START_TIME,
        pointerId = 0,
        touches = emptyList(),
        changedTouches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 3f, GESTURE_START_TIME, 0))
      )
    )

  /** Events (2 pointer): START 1st -> START 2nd -> MOVE 1st -> UP 2st -> UP 1st */
  private val startPointerMoveUpSequence =
    listOf(
      createTouchEvent(
        gestureTime = GESTURE_START_TIME,
        action = MotionEvent.ACTION_DOWN,
        pointerId = 0,
        pointerIds = intArrayOf(0),
        pointerCoords = arrayOf(pointerCoords(1f, 1f))
      ),
      createTouchEvent(
        gestureTime = GESTURE_START_TIME,
        action = MotionEvent.ACTION_POINTER_DOWN,
        pointerId = 1,
        pointerIds = intArrayOf(0, 1),
        pointerCoords = arrayOf(pointerCoords(1f, 1f), pointerCoords(2f, 1f))
      ),
      createTouchEvent(
        gestureTime = GESTURE_START_TIME,
        action = MotionEvent.ACTION_MOVE,
        pointerId = 0,
        pointerIds = intArrayOf(0, 1),
        pointerCoords = arrayOf(pointerCoords(1f, 2f), pointerCoords(2f, 1f))
      ),
      createTouchEvent(
        gestureTime = GESTURE_START_TIME,
        action = MotionEvent.ACTION_POINTER_UP,
        pointerId = 1,
        pointerIds = intArrayOf(0, 1),
        pointerCoords = arrayOf(pointerCoords(1f, 2f), pointerCoords(2f, 1f))
      ),
      createTouchEvent(
        gestureTime = GESTURE_START_TIME,
        action = MotionEvent.ACTION_POINTER_UP,
        pointerId = 0,
        pointerIds = intArrayOf(0),
        pointerCoords = arrayOf(pointerCoords(1f, 2f))
      )
    )

  /** Expected values for [startPointerMoveUpSequence] */
  private val startPointerMoveUpExpectedSequence =
    listOf(
      /*
       * START event for touch 1:
       * {
       *   touch: 0,
       *   touches: [touch1],
       *   changed: [touch1]
       * }
       */
      buildGestureEvent(
        surfaceId = SURFACE_ID,
        viewTag = TARGET_VIEW_ID,
        locationX = 1f,
        locationY = 1f,
        time = GESTURE_START_TIME,
        pointerId = 0,
        touches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 1f, GESTURE_START_TIME, 0)),
        changedTouches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 1f, GESTURE_START_TIME, 0))
      ),
      /*
       * START event for touch 2:
       * {
       *   touch: 1,
       *   touches: [touch0, touch1],
       *   changed: [touch1]
       * }
       */
      buildGestureEvent(
        surfaceId = SURFACE_ID,
        viewTag = TARGET_VIEW_ID,
        locationX = 2f,
        locationY = 1f,
        time = GESTURE_START_TIME,
        pointerId = 1,
        touches =
        listOf(
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 1f, GESTURE_START_TIME, 0),
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 2f, 1f, GESTURE_START_TIME, 1)
        ),
        changedTouches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 2f, 1f, GESTURE_START_TIME, 1))
      ),
      /*
       * MOVE event for touch 1:
       * {
       *   touch: 0,
       *   touches: [touch0, touch1],
       *   changed: [touch0, touch1]
       * }
       * {
       *   touch: 1,
       *   touches: [touch0, touch1],
       *   changed: [touch0, touch1]
       * }
       */
      buildGestureEvent(
        surfaceId = SURFACE_ID,
        viewTag = TARGET_VIEW_ID,
        locationX = 1f,
        locationY = 2f,
        time = GESTURE_START_TIME,
        pointerId = 0,
        touches =
        listOf(
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 2f, GESTURE_START_TIME, 0),
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 2f, 1f, GESTURE_START_TIME, 1)
        ),
        changedTouches =
        listOf(
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 2f, GESTURE_START_TIME, 0),
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 2f, 1f, GESTURE_START_TIME, 1)
        )
      ),
      buildGestureEvent(
        surfaceId = SURFACE_ID,
        viewTag = TARGET_VIEW_ID,
        locationX = 2f,
        locationY = 1f,
        time = GESTURE_START_TIME,
        pointerId = 1,
        touches =
        listOf(
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 2f, GESTURE_START_TIME, 0),
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 2f, 1f, GESTURE_START_TIME, 1)
        ),
        changedTouches =
        listOf(
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 2f, GESTURE_START_TIME, 0),
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 2f, 1f, GESTURE_START_TIME, 1)
        )
      ),
      /*
       * UP event pointer 1:
       * {
       *   touch: 1,
       *   touches: [touch0],
       *   changed: [touch1]
       * }
       */
      buildGestureEvent(
        surfaceId = SURFACE_ID,
        viewTag = TARGET_VIEW_ID,
        locationX = 2f,
        locationY = 1f,
        time = GESTURE_START_TIME,
        pointerId = 1,
        touches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 2f, GESTURE_START_TIME, 0)),
        changedTouches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 2f, 1f, GESTURE_START_TIME, 1))
      ),
      /*
       * UP event pointer 0:
       * {
       *   touch: 0,
       *   touches: [],
       *   changed: [touch0]
       * }
       */
      buildGestureEvent(
        surfaceId = SURFACE_ID,
        viewTag = TARGET_VIEW_ID,
        locationX = 1f,
        locationY = 2f,
        time = GESTURE_START_TIME,
        pointerId = 0,
        touches = emptyList(),
        changedTouches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 2f, GESTURE_START_TIME, 0))
      )
    )

  /** Events (2 pointer): START 1st -> START 2nd -> MOVE 1st -> CANCEL */
  private val startMoveCancelSequence =
    listOf(
      createTouchEvent(
        gestureTime = GESTURE_START_TIME,
        action = MotionEvent.ACTION_DOWN,
        pointerId = 0,
        pointerIds = intArrayOf(0),
        pointerCoords = arrayOf(pointerCoords(1f, 1f))
      ),
      createTouchEvent(
        gestureTime = GESTURE_START_TIME,
        action = MotionEvent.ACTION_POINTER_DOWN,
        pointerId = 1,
        pointerIds = intArrayOf(0, 1),
        pointerCoords = arrayOf(pointerCoords(1f, 1f), pointerCoords(2f, 1f))
      ),
      createTouchEvent(
        gestureTime = GESTURE_START_TIME,
        action = MotionEvent.ACTION_MOVE,
        pointerId = 0,
        pointerIds = intArrayOf(0, 1),
        pointerCoords = arrayOf(pointerCoords(1f, 2f), pointerCoords(2f, 1f))
      ),
      createTouchEvent(
        gestureTime = GESTURE_START_TIME,
        action = MotionEvent.ACTION_CANCEL,
        pointerId = 0,
        pointerIds = intArrayOf(0, 1),
        pointerCoords = arrayOf(pointerCoords(1f, 3f), pointerCoords(2f, 1f))
      )
    )

  /** Expected values for [startMoveCancelSequence] */
  private val startMoveCancelExpectedSequence =
    listOf(
      /*
       * START event for touch 1:
       * {
       *   touch: 0,
       *   touches: [touch1],
       *   changed: [touch1]
       * }
       */
      buildGestureEvent(
        surfaceId = SURFACE_ID,
        viewTag = TARGET_VIEW_ID,
        locationX = 1f,
        locationY = 1f,
        time = GESTURE_START_TIME,
        pointerId = 0,
        touches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 1f, GESTURE_START_TIME, 0)),
        changedTouches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 1f, GESTURE_START_TIME, 0))
      ),
      /*
       * START event for touch 2:
       * {
       *   touch: 1,
       *   touches: [touch0, touch1],
       *   changed: [touch1]
       * }
       */
      buildGestureEvent(
        surfaceId = SURFACE_ID,
        viewTag = TARGET_VIEW_ID,
        locationX = 2f,
        locationY = 1f,
        time = GESTURE_START_TIME,
        pointerId = 1,
        touches =
        listOf(
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 1f, GESTURE_START_TIME, 0),
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 2f, 1f, GESTURE_START_TIME, 1)
        ),
        changedTouches =
        listOf(buildGesture(SURFACE_ID, TARGET_VIEW_ID, 2f, 1f, GESTURE_START_TIME, 1))
      ),
      /*
       * MOVE event for touch 1:
       * {
       *   touch: 0,
       *   touches: [touch0, touch1],
       *   changed: [touch0, touch1]
       * }
       * {
       *   touch: 1,
       *   touches: [touch0, touch1],
       *   changed: [touch0, touch1]
       * }
       */
      buildGestureEvent(
        surfaceId = SURFACE_ID,
        viewTag = TARGET_VIEW_ID,
        locationX = 1f,
        locationY = 2f,
        time = GESTURE_START_TIME,
        pointerId = 0,
        touches =
        listOf(
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 2f, GESTURE_START_TIME, 0),
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 2f, 1f, GESTURE_START_TIME, 1)
        ),
        changedTouches =
        listOf(
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 2f, GESTURE_START_TIME, 0),
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 2f, 1f, GESTURE_START_TIME, 1)
        )
      ),
      buildGestureEvent(
        SURFACE_ID,
        TARGET_VIEW_ID,
        2f,
        1f,
        GESTURE_START_TIME,
        1,
        listOf(
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 2f, GESTURE_START_TIME, 0),
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 2f, 1f, GESTURE_START_TIME, 1)
        ),
        listOf(
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 2f, GESTURE_START_TIME, 0),
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 2f, 1f, GESTURE_START_TIME, 1)
        )
      ),
      /*
       * CANCEL event:
       * {
       *   touch: 0,
       *   touches: [],
       *   changed: [touch0, touch1]
       * }
       * {
       *   touch: 1,
       *   touches: [],
       *   changed: [touch0, touch1]
       * }
       */
      buildGestureEvent(
        surfaceId = SURFACE_ID,
        viewTag = TARGET_VIEW_ID,
        locationX = 1f,
        locationY = 3f,
        time = GESTURE_START_TIME,
        pointerId = 0,
        touches = emptyList(),
        changedTouches =
        listOf(
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 3f, GESTURE_START_TIME, 0),
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 2f, 1f, GESTURE_START_TIME, 1)
        )
      ),
      buildGestureEvent(
        surfaceId = SURFACE_ID,
        viewTag = TARGET_VIEW_ID,
        locationX = 2f,
        locationY = 1f,
        time = GESTURE_START_TIME,
        pointerId = 1,
        touches = emptyList(),
        changedTouches =
        listOf(
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 1f, 3f, GESTURE_START_TIME, 0),
          buildGesture(SURFACE_ID, TARGET_VIEW_ID, 2f, 1f, GESTURE_START_TIME, 1)
        )
      )
    )
  private var dispatchedEvents: List<ReadableMap> = emptyList()
  private lateinit var eventEmitter: FabricEventEmitter
  private lateinit var uiManager: FabricUIManager

  @Before
  fun setUp() {
    mockStatic(Arguments::class.java)
    mockStatic(FabricUIManager::class.java)
    whenever(Arguments.createArray()).thenAnswer { JavaOnlyArray() }
    whenever(Arguments.createMap()).thenAnswer { JavaOnlyMap() }
    val metrics = DisplayMetrics()
    metrics.xdpi = 1f
    metrics.ydpi = 1f
    metrics.density = 1f
    DisplayMetricsHolder.setWindowDisplayMetrics(metrics)
    uiManager = mock(FabricUIManager::class.java)
    eventEmitter = FabricEventEmitter(uiManager)
  }

  @Test
  fun testFabric_startMoveEnd() {
    for (event in startMoveEndSequence) {
      event.dispatchModern(eventEmitter)
    }
    val argument = ArgumentCaptor.forClass(WritableMap::class.java)
    verify(uiManager, times(4))
      .receiveEvent(
        anyInt(),
        anyInt(),
        anyString(),
        anyBoolean(),
        anyInt(),
        argument.capture(),
        anyInt()
      )
    Assert.assertEquals(startMoveEndExpectedSequence, argument.allValues)
  }

  @Test
  fun testFabric_startMoveCancel() {
    for (event in startMoveCancelSequence) {
      event.dispatchModern(eventEmitter)
    }
    val argument = ArgumentCaptor.forClass(WritableMap::class.java)
    verify(uiManager, times(6))
      .receiveEvent(
        anyInt(),
        anyInt(),
        anyString(),
        anyBoolean(),
        anyInt(),
        argument.capture(),
        anyInt()
      )
    Assert.assertEquals(startMoveCancelExpectedSequence, argument.allValues)
  }

  @Test
  fun testFabric_startPointerUpCancel() {
    for (event in startPointerMoveUpSequence) {
      event.dispatchModern(eventEmitter)
    }
    val argument = ArgumentCaptor.forClass(WritableMap::class.java)
    verify(uiManager, times(6))
      .receiveEvent(
        anyInt(),
        anyInt(),
        anyString(),
        anyBoolean(),
        anyInt(),
        argument.capture(),
        anyInt()
      )
    Assert.assertEquals(startPointerMoveUpExpectedSequence, argument.allValues)
  }

  private fun createTouchEvent(
    gestureTime: Int,
    action: Int,
    pointerId: Int,
    pointerIds: IntArray,
    pointerCoords: Array<MotionEvent.PointerCoords>
  ): TouchEvent {
    touchEventCoalescingKeyHelper.addCoalescingKey(gestureTime.toLong())
    val action = action or (pointerId shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
    return TouchEvent.obtain(
      SURFACE_ID,
      TARGET_VIEW_ID,
      getType(action),
      MotionEvent.obtain(
        gestureTime.toLong(),
        gestureTime.toLong(),
        action,
        pointerIds.size,
        pointerIds,
        pointerCoords,
        0,
        0f,
        0f,
        0,
        0,
        0,
        0
      ),
      gestureTime.toLong(),
      pointerCoords[0].x,
      pointerCoords[0].y,
      touchEventCoalescingKeyHelper
    )
  }

  companion object {
    private const val SURFACE_ID = 121
    private const val TARGET_VIEW_ID = 42
    private const val GESTURE_START_TIME = 1

    private fun getType(action: Int): TouchEventType {
      val action = action and MotionEvent.ACTION_POINTER_INDEX_MASK.inv()
      when (action) {
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_POINTER_DOWN -> return TouchEventType.START
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_POINTER_UP -> return TouchEventType.END
        MotionEvent.ACTION_MOVE -> return TouchEventType.MOVE
        MotionEvent.ACTION_CANCEL -> return TouchEventType.CANCEL
      }
      return TouchEventType.START
    }

    private fun buildGestureEvent(
      surfaceId: Int,
      viewTag: Int,
      locationX: Float,
      locationY: Float,
      time: Int,
      pointerId: Int,
      touches: List<WritableMap>,
      changedTouches: List<WritableMap>
    ): ReadableMap =
      buildGesture(surfaceId, viewTag, locationX, locationY, time, pointerId).apply {
        putArray("changedTouches", JavaOnlyArray.from(changedTouches))
        putArray("touches", JavaOnlyArray.from(touches))
      }

    private fun buildGesture(
      surfaceId: Int,
      viewTag: Int,
      locationX: Float,
      locationY: Float,
      time: Int,
      pointerId: Int
    ): WritableMap =
      JavaOnlyMap().apply {
        putInt("targetSurface", surfaceId)
        putInt("target", viewTag)
        putDouble("locationX", locationX.toDouble())
        putDouble("locationY", locationY.toDouble())
        putDouble("pageX", locationX.toDouble())
        putDouble("pageY", locationY.toDouble())
        putDouble("identifier", pointerId.toDouble())
        putDouble("timestamp", time.toDouble())
      }

    private fun pointerCoords(x: Float, y: Float): MotionEvent.PointerCoords =
      MotionEvent.PointerCoords().apply {
        this.x = x
        this.y = y
      }
  }
}
