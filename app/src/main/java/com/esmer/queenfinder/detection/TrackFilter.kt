package com.esmer.queenfinder.detection

/**
 * Follows queen detections across frames so a single-frame false positive does not
 * trigger an alert. A track becomes "stable" after [stableFrames] consecutive hits
 * and survives up to [maxMisses] frames without a matching detection.
 *
 * Not thread safe: call [update] from the inference thread only.
 */
class TrackFilter(
    var stableFrames: Int = 5,
    private val maxMisses: Int = 3,
    private val matchIou: Float = 0.3f,
    private val smoothing: Float = 0.5f,
) {
    private class Track(var box: Box, var score: Float) {
        var hits = 1
        var misses = 0
        var alerted = false
    }

    private val tracks = ArrayList<Track>()

    /** True when a track crossed the stable threshold in this update (fire an alert). */
    var newlyStable: Boolean = false
        private set

    fun reset() {
        tracks.clear()
        newlyStable = false
    }

    /**
     * Feed the queen detections of one frame. Returns the best stable queen, if any.
     */
    fun update(queens: List<Detection>): StableQueen? {
        newlyStable = false
        val matched = BooleanArray(queens.size)

        // Greedy matching: each existing track takes its best overlapping detection.
        for (track in tracks) {
            var bestIdx = -1
            var bestIou = matchIou
            for (i in queens.indices) {
                if (matched[i]) continue
                val overlap = iou(track.box, queens[i].box)
                if (overlap >= bestIou) {
                    bestIou = overlap
                    bestIdx = i
                }
            }
            if (bestIdx >= 0) {
                matched[bestIdx] = true
                val det = queens[bestIdx]
                track.box = lerp(track.box, det.box, smoothing)
                track.score = det.score
                track.hits++
                track.misses = 0
                if (track.hits >= stableFrames && !track.alerted) {
                    track.alerted = true
                    newlyStable = true
                }
            } else {
                track.misses++
            }
        }

        tracks.removeAll { it.misses > maxMisses }

        for (i in queens.indices) {
            if (!matched[i]) tracks.add(Track(queens[i].box, queens[i].score))
        }

        val stable = tracks
            .filter { it.hits >= stableFrames && it.misses == 0 }
            .maxByOrNull { it.hits }
            ?: return null
        return StableQueen(stable.box, stable.score, stable.hits)
    }

    private fun lerp(from: Box, to: Box, t: Float): Box = Box(
        from.left + (to.left - from.left) * t,
        from.top + (to.top - from.top) * t,
        from.right + (to.right - from.right) * t,
        from.bottom + (to.bottom - from.bottom) * t,
    )
}
