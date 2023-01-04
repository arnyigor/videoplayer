package com.arny.mobilecinema.presentation.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.CoroutineScope
import java.io.File
import kotlin.math.roundToInt
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

fun Context.getDrawableCompat(@DrawableRes drawableRes: Int): Drawable? =
    AppCompatResources.getDrawable(this, drawableRes)

fun ImageView.setTint(@ColorRes id: Int) {
    ImageViewCompat.setImageTintList(
        this,
        ColorStateList.valueOf(ContextCompat.getColor(context, id))
    )
}

fun Int.toPx(context: Context): Int =
    (this * (context.resources.displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()

fun Context.getDrawableColor(@ColorRes color: Int): Int =
    ContextCompat.getColor(this, color)

/**
 * @param drawableRes resource of drawable
 * @param position position of drawable(0 - left,1 - top,2 - end,3 - bottom)
 * @param color resource of color
 */
fun TextView.setDrawableResWithTint(
    @DrawableRes drawableRes: Int?,
    position: Int,
    @ColorRes color: Int? = null
) {
    drawableRes?.let(context::getDrawableCompat)?.also { drawable ->
        color?.let { clr ->
            this.setDrawableWithTint(drawable, position, clr)
        }
    } ?: run {
        this.setDrawableWithTint(null, position, null)
    }
}

fun TextView.setDrawableWithTint(drawable: Drawable?, position: Int, @ColorInt color: Int? = null) {
    when (position) {
        0 -> {
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                getTintedDrawable(drawable, color) ?: compoundDrawablesRelative[position],
                compoundDrawablesRelative[1],
                compoundDrawablesRelative[2],
                compoundDrawablesRelative[3]
            )
        }

        1 -> {
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                compoundDrawablesRelative[0],
                getTintedDrawable(drawable, color) ?: compoundDrawablesRelative[position],
                compoundDrawablesRelative[2],
                compoundDrawablesRelative[3]
            )
        }

        2 -> {
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                compoundDrawablesRelative[0],
                compoundDrawablesRelative[1],
                getTintedDrawable(drawable, color) ?: compoundDrawablesRelative[position],
                compoundDrawablesRelative[3]
            )
        }

        3 -> {
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                compoundDrawablesRelative[0],
                compoundDrawablesRelative[1],
                compoundDrawablesRelative[2],
                getTintedDrawable(drawable, color) ?: compoundDrawablesRelative[position]
            )
        }

        else -> {
        }
    }
}

private fun getTintedDrawable(drawable: Drawable?, color: Int?): Drawable? {
    return drawable?.also { dr ->
        color?.also { tintColor ->
            dr.mutate().colorFilter =
                PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_ATOP)
        }
    }
}

fun EditText?.doAfterEnterClick(onChanged: () -> Unit, vararg actionIds: Int) {
    this?.setOnEditorActionListener { _: TextView?, actionId: Int, event: KeyEvent? ->
        when {
            actionId == EditorInfo.IME_ACTION_DONE
                    || actionId in actionIds || event != null
                    && event.action == KeyEvent.ACTION_DOWN &&
                    event.keyCode == KeyEvent.KEYCODE_ENTER -> {
                onChanged()
                true
            }

            else -> false
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
fun TextView.setOnRightDrawerClickListener(onClick: () -> Unit) {
    this.setOnTouchListener(object : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (event != null && event.action == MotionEvent.ACTION_DOWN) {
                if (event.rawX >= (this@setOnRightDrawerClickListener.right - this@setOnRightDrawerClickListener.compoundDrawables[2].bounds.width())) {
                    onClick()
                    return true
                }
            }
            return false
        }
    })
}

@SuppressLint("ClickableViewAccessibility")
fun TextView.setOnLeftDrawerClickListener(onClick: () -> Unit) {
    this.setOnTouchListener(object : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (event != null && event.action == MotionEvent.ACTION_DOWN) {
                if (event.rawX <= (this@setOnLeftDrawerClickListener.compoundDrawables[0].bounds.width())) {
                    onClick()
                    return true
                }
            }
            return false
        }
    })
}

fun Fragment.updateTitle(title: String?) {
    (requireActivity() as AppCompatActivity).supportActionBar?.title = title
}

fun <T> autoClean(init: () -> T): ReadOnlyProperty<Fragment, T> = AutoClean(init)

private class AutoClean<T>(private val init: () -> T) : ReadOnlyProperty<Fragment, T>,
    LifecycleEventObserver {
    private var cached: T? = null

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        return cached ?: init().also { newValue ->
            cached = newValue
            thisRef.viewLifecycleOwner.lifecycle.addObserver(this)
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            cached = null
            source.lifecycle.removeObserver(this)
        }
    }
}

@SuppressLint("BatteryLife")
fun Fragment.setBatteryNoSafe() {
    try {
        val intent: Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:" + context?.packageName)
        }
        requireContext().packageManager.resolveActivity(intent, 0)?.let {
            startActivity(intent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun Fragment.openAppSettings() {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:" + context?.packageName)
        }
        requireContext().packageManager.resolveActivity(intent, 0)?.let {
            startActivity(intent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun Fragment.isAppInWhiteList(): Boolean {
    return try {
        val powerManager =
            context?.applicationContext?.getSystemService(POWER_SERVICE) as PowerManager?
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context?.packageName) ?: false
        } else {
            false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun Context.share(string: String?, title: String?) {
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, string)
    }.also { intent ->
        startActivity(Intent.createChooser(intent, title))
    }
}

fun Context.shareFile(file: File, title: String?) {
    Intent(Intent.ACTION_SEND).apply {
        type = "text/*"
        putExtra(
            Intent.EXTRA_STREAM,
            FileProvider.getUriForFile(this@shareFile, "$packageName.provider", file)
        )
    }.also { intent ->
        startActivity(Intent.createChooser(intent, title))
    }
}

fun Fragment.requestPermission(
    resultLauncher: ActivityResultLauncher<String>,
    permission: String,
    checkPermissionOk: () -> Unit = {},
    onNeverAskAgain: () -> Unit = {}
) {
    when {
        ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED -> {
            checkPermissionOk()
        }

        !shouldShowRequestPermissionRationale(permission) -> {
            onNeverAskAgain()
        }

        else -> {
            resultLauncher.launch(permission)
        }
    }
}

fun Context.getImg(@DrawableRes res: Int): Drawable? {
    return ContextCompat.getDrawable(this, res)
}

fun ImageView.setImgFromUrl(
    url: String,
    @DrawableRes placeholderRes: Int = android.R.drawable.ic_media_play,
    @DrawableRes errorRes: Int = android.R.drawable.ic_media_play
) {
    Glide.with(context)
        .load(url)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .placeholder(placeholderRes)
        .error(errorRes)
        .centerCrop()
        .override(200, 200)
        .into(this)
}

fun Context.downloadBitmap(url: String?): Bitmap? =
    Glide.with(this).asBitmap().load(url).submit().get()

fun ImageView.setImgFromBitmap(
    bitmap: Bitmap,
    @DrawableRes placeholderRes: Int = android.R.drawable.ic_media_play,
    @DrawableRes errorRes: Int = android.R.drawable.ic_media_play
) {
    Glide.with(context)
        .load(bitmap)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .placeholder(placeholderRes)
        .error(errorRes)
        .centerCrop()
        .override(200, 200)
        .into(this)
}

fun ImageView.setImgFromDrawable(
    @DrawableRes drawableRes: Int,
    @DrawableRes placeholderRes: Int = android.R.drawable.ic_media_play,
    @DrawableRes errorRes: Int = android.R.drawable.ic_media_play
) {
    Glide.with(context)
        .load(drawableRes)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .placeholder(placeholderRes)
        .error(errorRes)
        .centerCrop()
        .override(200, 200)
        .into(this)
}

fun isOreoPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

fun Fragment.sendServiceMessage(cls: Class<Any>, action: String, extras: Bundle.() -> Unit = {}) {
    requireContext().sendServiceMessage(cls, action, extras)
}

fun Context.sendServiceMessage(
    cls: Class<Any>,
    action: String,
    extras: Bundle.() -> Unit = {}
) {
    Intent(this.applicationContext, cls).apply {
        this.action = action
        this.putExtras(Bundle().apply(extras))
        if (isOreoPlus()) {
            this@sendServiceMessage.applicationContext.startForegroundService(this)
        } else {
            this@sendServiceMessage.startService(this)
        }
    }
}

fun Context.bind(cls: Class<Any>, connection: ServiceConnection): Boolean {
    var bound: Boolean
    Intent(this, cls).also { intent ->
        bound = bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }
    return bound
}

fun Context.unbind(connection: ServiceConnection) {
    unbindService(connection)
}

fun Fragment.launchWhenCreated(block: suspend CoroutineScope.() -> Unit) {
    viewLifecycleOwner.lifecycleScope.launchWhenCreated { block.invoke(this) }
}

fun Fragment.sendBroadcast(action: String, extras: Bundle.() -> Unit = {}) {
    val intent = Intent(action).apply {
        this.putExtras(Bundle().apply(extras))
    }
    LocalBroadcastManager.getInstance(requireContext().applicationContext).sendBroadcast(intent)
}

fun Fragment.registerReceiver(action: String, receiver: BroadcastReceiver) {
    LocalBroadcastManager.getInstance(requireContext().applicationContext)
        .registerReceiver(receiver, IntentFilter(action))
}

fun Fragment.unregisterReceiver(receiver: BroadcastReceiver) {
    LocalBroadcastManager.getInstance(requireContext().applicationContext)
        .unregisterReceiver(receiver)
}

fun Fragment.setupSearchView(
    menuItem: MenuItem,
    onQueryChange: (text: String?) -> Unit = {},
    onSubmitAvailable: Boolean = false,
    onQuerySubmit: (text: String?) -> Unit = {},
    onMenuCollapse: () -> Unit = {}
) {
    val searchView = menuItem.actionView as SearchView
    searchView.isIconifiedByDefault = true
    searchView.isFocusable = true
    searchView.isIconified = false
    searchView.requestFocusFromTouch()
    val searchManager =
        requireActivity().getSystemService(Context.SEARCH_SERVICE) as? SearchManager
    searchManager?.let {
        searchView.setSearchableInfo(searchManager.getSearchableInfo(requireActivity().componentName))
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                onQuerySubmit(query)
                return onSubmitAvailable
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                onQueryChange(newText)
                return true
            }
        })
        menuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                item.actionView?.requestFocus()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                searchView.setQuery("", true)
                onMenuCollapse()
                return true
            }
        })
    }
}

fun Fragment.toastMessage(string: IWrappedString?) {
    string?.toString(requireContext()).takeIf { !it.isNullOrBlank() }?.let { text ->
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }
}

fun isOlderSDK(version: Int): Boolean = Build.VERSION.SDK_INT >= version