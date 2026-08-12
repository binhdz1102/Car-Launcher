package com.android.car.carlauncher.fixture

import android.app.Activity
import android.os.Bundle
import com.android.car.carlauncher.fixture.databinding.ActivityFixtureBinding

/** Shared XML/ViewBinding host for deterministic launchable fixture activities. */
abstract class FixtureActivity : Activity() {
    abstract val titleResId: Int

    private lateinit var binding: ActivityFixtureBinding
    private var interactionCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFixtureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        interactionCount = savedInstanceState?.getInt(STATE_INTERACTION_COUNT) ?: 0
        binding.fixtureTitle.text = getString(titleResId)
        binding.fixtureAction.setOnClickListener {
            interactionCount += 1
            renderStatus()
        }
        binding.fixtureFinish.setOnClickListener { finishAndRemoveTask() }
        renderStatus()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        renderStatus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_INTERACTION_COUNT, interactionCount)
        super.onSaveInstanceState(outState)
    }

    private fun renderStatus() {
        binding.fixtureStatus.text =
            "display=${display?.displayId ?: 0}; task=$taskId; interactions=$interactionCount"
    }

    private companion object {
        const val STATE_INTERACTION_COUNT = "interaction_count"
    }
}

class FixtureMapActivity : FixtureActivity() {
    override val titleResId: Int = R.string.fixture_map_title
}

class FixtureMediaActivity : FixtureActivity() {
    override val titleResId: Int = R.string.fixture_media_title
}

class FixtureUtilityActivity : FixtureActivity() {
    override val titleResId: Int = R.string.fixture_utility_title
}

class FixtureRestrictedActivity : FixtureActivity() {
    override val titleResId: Int = R.string.fixture_restricted_title
}
