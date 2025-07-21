package com.arny.mobilecinema.presentation.playerview


/*private fun initMoreLinksPopup() {
    if (movie?.type == MovieType.CINEMA) {
        val cinemaUrlData = movie?.cinemaUrlData
        val hdUrls = cinemaUrlData?.hdUrl?.urls.orEmpty()
        val cinemaUrls = cinemaUrlData?.cinemaUrl?.urls.orEmpty()
        val fullLinkList = hdUrls + cinemaUrls
        val popupItems = fullLinkList.mapIndexed { index, s -> "Ссылка ${index + 1}" to s }
        val notEmpty = fullLinkList.isNotEmpty()
        binding.ivMoreLink.isVisible = notEmpty
        if (notEmpty) {
            moreLinkPopUp = PopupMenu(requireContext(), binding.ivMoreLink)
            for ((i, items) in popupItems.withIndex()) {
                moreLinkPopUp?.menu?.add(0, i, 0, items.first)
            }
            moreLinkPopUp?.setOnMenuItemClickListener { menuItem ->
                launchWhenCreated {
                    setMediaSources(
                        path = popupItems[menuItem.itemId].second,
                        time = getTimePosition(player?.currentPosition ?: 0),
                        movie = movie,
                    )
                }
                true
            }
        }
    }
}*/

/*    private fun setQualityByConnection(list: ArrayList<Pair<String, TrackSelectionOverride>>) {
        val connectionType = getConnectionType(requireContext())
        val groupList = list.map { it.second }.map { it.mediaTrackGroup }
        val formats = groupList.mapIndexed { index, trackGroup -> trackGroup.getFormat(index) }
        val bitratesKbps = formats.map { it.bitrate.div(1024) }
        Timber.d("current QualityId:$qualityId")
        val newId =
            bitratesKbps.indexOfLast { it < connectionType.speedKbps }.takeIf { it >= 0 } ?: 0
        Timber.d("connectionType:$connectionType")
        Timber.d("bitrates:$bitratesKbps")
        Timber.d("selected qualityId:$qualityId")
        if (newId > qualityId) {// Check buufering time
            qualityId = newId
            Timber.d("set new qualityId:$qualityId")
            list.getOrNull(qualityId)?.second?.let { setQuality(it) }
        }
    }*/

/*private fun setSubTitles(trackSelectionOverride: TrackSelectionOverride) {
    trackSelector?.let { selector ->
        selector.parameters = selector.parameters
            .buildUpon()
            .clearOverrides()
            .setSelectUndeterminedTextLanguage(true)
            .setRendererDisabled(C.TRACK_TYPE_TEXT, false)
            .addOverride(trackSelectionOverride)
            .setTunnelingEnabled(true)
            .build()
    }
}*/