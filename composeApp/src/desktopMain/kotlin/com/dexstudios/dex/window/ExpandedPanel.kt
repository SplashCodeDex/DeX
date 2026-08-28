package com.dexstudios.dex.window

import com.dexstudios.dex.platform.DockCardMetrics

/**
 * Enumeration of available left-hand drawer expansion panels.
 * Widths are the single rendered truth; window-placement math derives its deltas
 * from these values so UI geometry and placement physics can never drift apart.
 */
enum class ExpandedPanel(val expandedWidth: Int) {
    FileExplorer(DockCardMetrics.FILE_EXPLORER_WIDTH_EXPANDED),
    Settings(DockCardMetrics.SETTINGS_WIDTH_EXPANDED),
    Pairing(DockCardMetrics.CARD_WIDTH_CONTRACTED),
    DeviceStatus(DockCardMetrics.CARD_WIDTH_CONTRACTED),
    DeviceStatusTablet(DockCardMetrics.CARD_WIDTH_CONTRACTED),
    DeviceStatusWatch(DockCardMetrics.CARD_WIDTH_CONTRACTED),
    DeviceStatusLaptop(DockCardMetrics.CARD_WIDTH_CONTRACTED),
    ;

    val isContractedHeight: Boolean
        get() = this == Pairing || this == DeviceStatus || this == DeviceStatusTablet || this == DeviceStatusWatch || this == DeviceStatusLaptop
}
