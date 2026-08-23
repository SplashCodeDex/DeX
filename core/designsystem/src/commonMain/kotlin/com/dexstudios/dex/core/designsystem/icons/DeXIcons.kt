package com.dexstudios.dex.core.designsystem.icons

import com.dexstudios.dex.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

/**
 * Centralized registry of all Fluent and system icon resources in DeX.
 *
 * Provides strongly-typed, categorized access to static [DrawableResource] icons,
 * avoiding direct raw resource references across feature modules.
 */
object DeXIcons {
    // Actions & Navigation
    val ArrowBack: DrawableResource = Res.drawable.ic_fluent_arrow_back
    val ArrowUpward: DrawableResource = Res.drawable.ic_fluent_arrow_upward
    val ArrowUploadArrow: DrawableResource = Res.drawable.ic_fluent_arrow_upload_arrow
    val ArrowDownloadArrow: DrawableResource = Res.drawable.ic_fluent_arrow_download_arrow
    val ArrowUploadCircle: DrawableResource = Res.drawable.ic_fluent_arrow_upload_circle
    val ArrowUploadReady: DrawableResource = Res.drawable.ic_fluent_arrow_upload_ready
    val Check: DrawableResource = Res.drawable.ic_fluent_check
    val CheckCircle: DrawableResource = Res.drawable.ic_fluent_check_circle
    val CheckCircleOutlined: DrawableResource = Res.drawable.ic_fluent_check_circle_outlined
    val Close: DrawableResource = Res.drawable.ic_fluent_close
    val Delete: DrawableResource = Res.drawable.ic_fluent_delete
    val Edit: DrawableResource = Res.drawable.ic_fluent_edit
    val ExpandMore: DrawableResource = Res.drawable.ic_fluent_expand_more
    val FilterList: DrawableResource = Res.drawable.ic_fluent_filter_list
    val MoreVert: DrawableResource = Res.drawable.ic_fluent_more_vert
    val Pin: DrawableResource = Res.drawable.ic_fluent_pin
    val Search: DrawableResource = Res.drawable.ic_fluent_search
    val Send: DrawableResource = Res.drawable.ic_fluent_send
    val Share: DrawableResource = Res.drawable.ic_fluent_share
    val IosShare: DrawableResource = Res.drawable.ic_fluent_ios_share
    val Sort: DrawableResource = Res.drawable.ic_fluent_sort

    // Content & Files
    val Article: DrawableResource = Res.drawable.ic_fluent_article
    val Clipboard: DrawableResource = Res.drawable.ic_fluent_clipboard
    val Cloud: DrawableResource = Res.drawable.ic_fluent_cloud
    val CloudDownload: DrawableResource = Res.drawable.ic_fluent_cloud_download
    val CloudUpload: DrawableResource = Res.drawable.ic_fluent_cloud_upload
    val FileDownload: DrawableResource = Res.drawable.ic_fluent_file_download
    val FileUpload: DrawableResource = Res.drawable.ic_fluent_file_upload
    val Folder: DrawableResource = Res.drawable.ic_fluent_folder
    val GridView: DrawableResource = Res.drawable.ic_fluent_grid_view
    val History: DrawableResource = Res.drawable.ic_fluent_history
    val Inventory: DrawableResource = Res.drawable.ic_fluent_inventory
    val Photo: DrawableResource = Res.drawable.ic_fluent_photo
    val VideoCamera: DrawableResource = Res.drawable.ic_fluent_video_camera
    val ViewList: DrawableResource = Res.drawable.ic_fluent_view_list

    // System & Devices
    val AccountCircle: DrawableResource = Res.drawable.ic_fluent_account_circle
    val AlertFilled: DrawableResource = Res.drawable.ic_fluent_alert_filled
    val AlertOnFilled: DrawableResource = Res.drawable.ic_fluent_alert_on_filled
    val Computer: DrawableResource = Res.drawable.ic_fluent_computer
    val Devices: DrawableResource = Res.drawable.ic_fluent_devices
    val DoNotDisturb: DrawableResource = Res.drawable.ic_fluent_do_not_disturb
    val Notifications: DrawableResource = Res.drawable.ic_fluent_notifications
    val Palette: DrawableResource = Res.drawable.ic_fluent_palette
    val PowerFilled: DrawableResource = Res.drawable.ic_fluent_power_filled
    val PowerSettingsNew: DrawableResource = Res.drawable.ic_fluent_power_settings_new
    val QrCode: DrawableResource = Res.drawable.ic_fluent_qr_code
    val QrCodeScanner: DrawableResource = Res.drawable.ic_fluent_qr_code_scanner
    val Settings: DrawableResource = Res.drawable.ic_fluent_settings
    val Smartphone: DrawableResource = Res.drawable.ic_fluent_smartphone
    val TouchApp: DrawableResource = Res.drawable.ic_fluent_touch_app
    val Tune: DrawableResource = Res.drawable.ic_fluent_tune
    val Wifi: DrawableResource = Res.drawable.ic_fluent_wifi

    // Battery Levels & Status
    val Battery1: DrawableResource = Res.drawable.ic_fluent_battery1
    val Battery2: DrawableResource = Res.drawable.ic_fluent_battery2
    val Battery3: DrawableResource = Res.drawable.ic_fluent_battery3
    val Battery4: DrawableResource = Res.drawable.ic_fluent_battery4
    val Battery5: DrawableResource = Res.drawable.ic_fluent_battery5
    val Battery6: DrawableResource = Res.drawable.ic_fluent_battery6
    val BatteryFull: DrawableResource = Res.drawable.ic_fluent_battery_full
    val BatteryCharging: DrawableResource = Res.drawable.ic_fluent_battery_charging

    // Utilities & Status
    val Bolt: DrawableResource = Res.drawable.ic_fluent_bolt
    val Info: DrawableResource = Res.drawable.ic_fluent_info
    val Warning: DrawableResource = Res.drawable.ic_fluent_warning
    val Google: DrawableResource = Res.drawable.ic_fluent_google
    val AppLogo: DrawableResource = Res.drawable.dex_logo
}
