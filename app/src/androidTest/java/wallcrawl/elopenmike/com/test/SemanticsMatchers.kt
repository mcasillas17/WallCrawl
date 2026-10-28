package wallcrawl.elopenmike.com.test

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher

/** Announced by accessibility services when its text changes. */
fun hasLiveRegion(): SemanticsMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
