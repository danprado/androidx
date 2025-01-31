/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.tv.material3

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.testutils.assertContainsColor
import androidx.compose.testutils.assertDoesNotContainColor
import androidx.compose.testutils.assertShape
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.tv.material3.tokens.Elevation
import com.google.common.truth.Truth
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun assertFloatPrecision(a: Float, b: Float) =
    Truth.assertThat(abs(a - b)).isLessThan(0.0001f)

@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalTestApi::class,
    ExperimentalTvMaterial3Api::class
)
@MediumTest
@RunWith(AndroidJUnit4::class)
class SurfaceTest {

    @get:Rule
    val rule = createComposeRule()

    private fun Int.toDp(): Dp = with(rule.density) { toDp() }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun originalOrderingWhenTheDefaultElevationIsUsed() {
        rule.setContent {
            Box(
                Modifier
                    .size(10.toDp())
                    .semantics(mergeDescendants = true) {}
                    .testTag("box")
            ) {
                Surface(
                    onClick = {},
                    shape = ClickableSurfaceDefaults.shape(
                        shape = RectangleShape
                    ),
                    color = ClickableSurfaceDefaults.color(
                        color = Color.Yellow
                    )
                ) {
                    Box(Modifier.fillMaxSize())
                }
                Surface(
                    onClick = {},
                    shape = ClickableSurfaceDefaults.shape(
                        shape = RectangleShape
                    ),
                    color = ClickableSurfaceDefaults.color(
                        color = Color.Green
                    )
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        rule.onNodeWithTag("box").captureToImage().assertShape(
            density = rule.density,
            shape = RectangleShape,
            shapeColor = Color.Green,
            backgroundColor = Color.White
        )
    }

    @Test
    fun absoluteElevationCompositionLocalIsSet() {
        var outerElevation: Dp? = null
        var innerElevation: Dp? = null
        rule.setContent {
            Surface(onClick = {}, tonalElevation = 2.toDp()) {
                outerElevation = LocalAbsoluteTonalElevation.current
                Surface(onClick = {}, tonalElevation = 4.toDp()) {
                    innerElevation = LocalAbsoluteTonalElevation.current
                }
            }
        }

        rule.runOnIdle {
            innerElevation?.let { nnInnerElevation ->
                assertFloatPrecision(nnInnerElevation.value, 6.toDp().value)
            }
            outerElevation?.let { nnOuterElevation ->
                assertFloatPrecision(nnOuterElevation.value, 2.toDp().value)
            }
        }
    }

    /**
     * Tests that composed modifiers applied to Surface are applied within the changes to
     * [LocalContentColor], so they can consume the updated values.
     */
    @Test
    fun contentColorSetBeforeModifier() {
        var contentColor: Color = Color.Unspecified
        val expectedColor = Color.Blue
        rule.setContent {
            CompositionLocalProvider(LocalContentColor provides Color.Red) {
                Surface(
                    modifier = Modifier.composed {
                        contentColor = LocalContentColor.current
                        Modifier
                    },
                    onClick = {},
                    tonalElevation = 2.toDp(),
                    contentColor = ClickableSurfaceDefaults.color(color = expectedColor)
                ) {}
            }
        }

        rule.runOnIdle {
            Truth.assertThat(contentColor).isEqualTo(expectedColor)
        }
    }

    @Test
    fun clickableOverload_semantics() {
        val count = mutableStateOf(0)
        rule.setContent {
            Surface(
                modifier = Modifier
                    .testTag("surface"),
                onClick = { count.value += 1 }
            ) {
                Text("${count.value}")
                Spacer(Modifier.size(30.toDp()))
            }
        }
        rule.onNodeWithTag("surface")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertHasClickAction()
            .assertIsEnabled()
            // since we merge descendants we should have text on the same node
            .assertTextEquals("0")
            .performKeyInput { pressKey(Key.DirectionCenter) }
            .assertTextEquals("1")
    }

    @Test
    fun clickableOverload_customSemantics() {
        val count = mutableStateOf(0)
        rule.setContent {
            Surface(
                modifier = Modifier
                    .testTag("surface"),
                onClick = { count.value += 1 },
            ) {
                Text("${count.value}")
                Spacer(Modifier.size(30.toDp()))
            }
        }
        rule.onNodeWithTag("surface")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertHasClickAction()
            .assertIsEnabled()
            // since we merge descendants we should have text on the same node
            .assertTextEquals("0")
            .performKeyInput { pressKey(Key.DirectionCenter) }
            .assertTextEquals("1")
    }

    @Test
    fun clickableOverload_clickAction() {
        val count = mutableStateOf(0)

        rule.setContent {
            Surface(
                modifier = Modifier
                    .testTag("surface"),
                onClick = { count.value += 1 }
            ) {
                Spacer(modifier = Modifier.size(30.toDp()))
            }
        }
        rule.onNodeWithTag("surface")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        Truth.assertThat(count.value).isEqualTo(1)

        rule.onNodeWithTag("surface").performKeyInput { pressKey(Key.DirectionCenter) }
            .performKeyInput { pressKey(Key.DirectionCenter) }
        Truth.assertThat(count.value).isEqualTo(3)
    }

    @Test
    fun clickableSurface_onDisable_clickFails() {
        val count = mutableStateOf(0f)
        val enabled = mutableStateOf(true)

        rule.setContent {
            Surface(
                modifier = Modifier
                    .testTag("surface"),
                onClick = { count.value += 1 },
                enabled = enabled.value
            ) {
                Spacer(Modifier.size(30.toDp()))
            }
        }
        rule.onNodeWithTag("surface")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsEnabled()
            .performKeyInput { pressKey(Key.DirectionCenter) }

        Truth.assertThat(count.value).isEqualTo(1)
        rule.runOnIdle {
            enabled.value = false
        }

        rule.onNodeWithTag("surface")
            .assertIsNotEnabled()
            .performKeyInput { pressKey(Key.DirectionCenter) }
            .performKeyInput { pressKey(Key.DirectionCenter) }
        Truth.assertThat(count.value).isEqualTo(1)
    }

    @Test
    fun clickableOverload_interactionSource() {
        val interactionSource = MutableInteractionSource()

        lateinit var scope: CoroutineScope

        rule.setContent {
            scope = rememberCoroutineScope()
            Surface(
                modifier = Modifier
                    .testTag("surface"),
                onClick = {},
                interactionSource = interactionSource
            ) {
                Spacer(Modifier.size(30.toDp()))
            }
        }

        val interactions = mutableListOf<Interaction>()

        scope.launch {
            interactionSource.interactions.collect { interactions.add(it) }
        }

        rule.runOnIdle {
            Truth.assertThat(interactions).isEmpty()
        }

        rule.onNodeWithTag("surface")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { keyDown(Key.DirectionCenter) }

        rule.runOnIdle {
            Truth.assertThat(interactions).hasSize(2)
            Truth.assertThat(interactions[1]).isInstanceOf(PressInteraction.Press::class.java)
        }

        rule.onNodeWithTag("surface").performKeyInput { keyUp(Key.DirectionCenter) }

        rule.runOnIdle {
            Truth.assertThat(interactions).hasSize(3)
            Truth.assertThat(interactions.first()).isInstanceOf(FocusInteraction.Focus::class.java)
            Truth.assertThat(interactions[1]).isInstanceOf(PressInteraction.Press::class.java)
            Truth.assertThat(interactions[2]).isInstanceOf(PressInteraction.Release::class.java)
            Truth.assertThat((interactions[2] as PressInteraction.Release).press)
                .isEqualTo(interactions[1])
        }
    }

    @Test
    fun clickableSurface_allowsFinalPassChildren() {
        val hitTested = mutableStateOf(false)

        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("surface"),
                    onClick = {}
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag("pressable")
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    hitTested.value = true
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    Truth
                                        .assertThat(event.changes[0].isConsumed)
                                        .isFalse()
                                }
                            }
                    )
                }
            }
        }
        rule.onNodeWithTag("surface").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.onNodeWithTag("pressable", true)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        Truth.assertThat(hitTested.value).isTrue()
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun clickableSurface_reactsToStateChange() {
        val interactionSource = MutableInteractionSource()
        var isPressed by mutableStateOf(false)

        rule.setContent {
            isPressed = interactionSource.collectIsPressedAsState().value
            Surface(
                modifier = Modifier
                    .testTag("surface")
                    .size(100.toDp()),
                onClick = {},
                interactionSource = interactionSource
            ) {}
        }

        with(rule.onNodeWithTag("surface")) {
            performSemanticsAction(SemanticsActions.RequestFocus)
            assertIsFocused()
            performKeyInput { keyDown(Key.DirectionCenter) }
        }

        rule.waitUntil(condition = { isPressed })

        Truth.assertThat(isPressed).isTrue()
    }

    @FlakyTest(bugId = 269229262)
    @RequiresApi(Build.VERSION_CODES.O)
    @Test
    fun clickableSurface_onFocus_changesGlowColor() {
        rule.setContent {
            Surface(
                modifier = Modifier
                    .testTag("surface")
                    .size(100.toDp()),
                onClick = {},
                color = ClickableSurfaceDefaults.color(
                    color = Color.Transparent,
                    focusedColor = Color.Transparent
                ),
                glow = ClickableSurfaceDefaults.glow(
                    glow = Glow(
                        elevationColor = Color.Magenta,
                        elevation = Elevation.Level5
                    ),
                    focusedGlow = Glow(
                        elevationColor = Color.Green,
                        elevation = Elevation.Level5
                    )
                )
            ) {}
        }
        rule.onNodeWithTag("surface")
            .captureToImage()
            .assertContainsColor(Color.Magenta)

        rule.onNodeWithTag("surface")
            .performSemanticsAction(SemanticsActions.RequestFocus)

        rule.onNodeWithTag("surface")
            .captureToImage()
            .assertContainsColor(Color.Green)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @Test
    fun clickableSurface_onFocus_changesScaleFactor() {
        rule.setContent {
            Box(
                modifier = Modifier
                    .background(Color.Blue)
                    .size(50.toDp())
            )
            Surface(
                onClick = {},
                modifier = Modifier
                    .size(50.toDp())
                    .testTag("surface"),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.5f
                )
            ) {}
        }
        rule.onRoot().captureToImage().assertContainsColor(Color.Blue)

        rule.onNodeWithTag("surface").performSemanticsAction(SemanticsActions.RequestFocus)

        rule.onRoot().captureToImage().assertDoesNotContainColor(Color.Blue)
    }
}
