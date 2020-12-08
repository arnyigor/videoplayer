package com.arny.videoplayer.presentation.utils

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding

fun <VB : ViewBinding> Fragment.viewBinding(
    destroy: ((VB) -> Unit)? = null,
    init: (View) -> VB
): Lazy<VB> {
    val delegate = ViewBindingDelegate(init, destroy, this)

    viewLifecycleOwnerLiveData.observe(this, { it.lifecycle.addObserver(delegate) })

    return delegate
}

private class ViewBindingDelegate<VB : ViewBinding>(
    private val init: (View) -> VB,
    private val destroy: ((VB) -> Unit)?,
    private val fragment: Fragment
) : Lazy<VB>, LifecycleEventObserver {

    private var cached: VB? = null

    override val value: VB
        get() = cached ?: init(fragment.requireView()).also {
            cached = it
        }

    override fun isInitialized(): Boolean = cached != null

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                if (isInitialized()) {
                    return
                }
                cached = init(fragment.requireView())
            }

            Lifecycle.Event.ON_DESTROY -> {
                destroy?.invoke(checkNotNull(cached))
                cached = null
                source.lifecycle.removeObserver(this)
            }

            else -> Unit
        }
    }
}
