package com.arny.mobilecinema.presentation.tv.library

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.doOnPreDraw
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.paging.PagingDataAdapter
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.recyclerview.widget.DiffUtil
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.tv.presenters.MovieCardPresenter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class TvLibraryFragment : VerticalGridSupportFragment() {
    private val model: TvLibraryViewModel by viewModel()
    private val movies = PagingDataAdapter(MovieCardPresenter(), object : DiffUtil.ItemCallback<ViewMovie>() {
        override fun areItemsTheSame(a: ViewMovie, b: ViewMovie) = a.dbId == b.dbId
        override fun areContentsTheSame(a: ViewMovie, b: ViewMovie) = a == b
    })
    private var status: LinearLayout? = null
    private var restored = false
    private var returnToCard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        returnToCard = savedInstanceState?.getBoolean("restore_card_focus") ?: false
        title = getString(if (model.isHistory) R.string.menu_history else R.string.menu_favorite)
        gridPresenter = VerticalGridPresenter().apply { numberOfColumns = 4 }
        adapter = movies
        setOnItemViewClickedListener { _, item, _, _ ->
            val movie = item as? ViewMovie ?: return@setOnItemViewClickedListener
            returnToCard = true
            findNavController().navigate(R.id.actionToDetails, bundleOf("movieId" to movie.dbId))
        }
        setOnItemViewSelectedListener { _, item, _, _ ->
            val movie = item as? ViewMovie ?: return@setOnItemViewSelectedListener
            if (restored) model.focus(movie.dbId, movies.snapshot().items.indexOfFirst { it.dbId == movie.dbId })
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val grid = requireNotNull(super.onCreateView(inflater, container, savedInstanceState))
        return FrameLayout(requireContext()).apply {
            addView(grid, FrameLayout.LayoutParams(-1, -1))
            status = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(ProgressBar(context))
                addView(TextView(context).apply { textSize = 22f; setTextColor(android.graphics.Color.WHITE) })
                addView(Button(context).apply { setText(R.string.retry); setOnClickListener { movies.retry() } })
            }
            addView(status, FrameLayout.LayoutParams(-1, -1))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.items.collectLatest { movies.submitData(it) } }
                launch { movies.loadStateFlow.collect { render(it) } }
            }
        }
    }

    private fun render(loads: CombinedLoadStates) {
        val overlay = status ?: return
        val empty = movies.size() == 0
        val loading = loads.refresh is LoadState.Loading
        val error = loads.refresh is LoadState.Error
        overlay.isVisible = empty
        overlay.getChildAt(0).isVisible = loading
        (overlay.getChildAt(1) as TextView).apply {
            isVisible = !loading
            setText(if (error) R.string.tv_catalog_error else R.string.tv_library_empty)
        }
        overlay.getChildAt(2).isVisible = error
        if (loading) restored = false
        if (empty || loading || restored) return
        val target = model.focusedId
        val index = movies.snapshot().items.indexOfFirst { it.dbId == target }
        if (index >= 0) {
            restored = true
            setSelectedPosition(index)
            restoreCardFocus()
        } else if (target != null && !loads.append.endOfPaginationReached && model.focusedPosition >= movies.size()) {
            setSelectedPosition(movies.size() - 1) // Load through to a previously focused later page.
        } else {
            restored = true
            setSelectedPosition(model.focusedPosition.coerceIn(0, movies.size() - 1))
            restoreCardFocus()
        }
    }

    private fun restoreCardFocus() {
        if (!returnToCard) return
        val grid = view?.findViewById<View>(androidx.leanback.R.id.browse_grid) ?: return
        grid.doOnPreDraw {
            if (view?.findViewById<View>(androidx.leanback.R.id.browse_grid) === grid && grid.requestFocus()) {
                returnToCard = false
            }
        }
        grid.invalidate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("restore_card_focus", returnToCard ||
            view?.findViewById<View>(androidx.leanback.R.id.browse_grid)?.hasFocus() == true)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        restored = false
        movies.refresh()
    }

    override fun onDestroyView() {
        status = null
        super.onDestroyView()
    }
}
