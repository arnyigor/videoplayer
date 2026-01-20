package com.arny.mobilecinema.presentation.favorite

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.databinding.DCustomOrderBinding
import com.arny.mobilecinema.databinding.DCustomSearchBinding
import com.arny.mobilecinema.databinding.FFavoritesBinding
import com.arny.mobilecinema.di.viewModelFactory
import com.arny.mobilecinema.presentation.home.VideoItemsAdapter
import com.arny.mobilecinema.presentation.listeners.OnSearchListener
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.createCustomLayoutDialog
import com.arny.mobilecinema.presentation.utils.hideKeyboard
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.navigateSafely
import com.arny.mobilecinema.presentation.utils.setupSearchView
import com.arny.mobilecinema.presentation.utils.updateTitle
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

/**
 * Экран «Избранное».
 */
class FavoritesFragment : Fragment(), OnSearchListener {

    @Inject lateinit var prefs: Prefs

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(): FavoritesViewModel
    }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: FavoritesViewModel by viewModelFactory { viewModelFactory.create() }

    /* UI‑поля */
    private lateinit var binding: FFavoritesBinding
    private var itemsAdapter: VideoItemsAdapter? = null
    private var searchMenuItem: MenuItem? = null
    private var searchView: SearchView? = null

    /* Состояние поиска */
    private var onQueryChangeSubmit = true
    private var emptySearch = true

    /* Текущие настройки сортировки и фильтрации */
    private var currentOrder: String = ""
    private var searchType: String = ""

    /* Для управления меню */
    private var hasSavedData = false

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateTitle(getString(R.string.f_favorites_title))
        initAdapters()
        observeData()
        initMenu()

        /* Загружаем избранное сразу после создания экрана */
        viewModel.reloadFavorites()
    }

    /* ------------------------------------------------------------------ */
    /** OnSearchListener ------------------------------------------------- */
    override fun isSearchComplete(): Boolean = emptySearch

    override fun collapseSearch() {
        viewModel.loadFavorites()
        emptySearch = true
    }
    /* ------------------------------------------------------------------ */

    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {

            /** Меню меняется в зависимости от наличия данных */
            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_search).isVisible = hasSavedData
                menu.findItem(R.id.action_search_settings).isVisible =
                    hasSavedData && !emptySearch
                menu.findItem(R.id.action_order_settings).isVisible = hasSavedData
                menu.findItem(R.id.menu_action_clear_cache).isVisible = hasSavedData
            }

            /** Создаём меню и SearchView */
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.favorites_menu, menu)
                searchMenuItem = menu.findItem(R.id.action_search)

                searchView = setupSearchView(
                    menuItem = searchMenuItem!!,
                    onQueryChange = { query ->
                        emptySearch = query?.isBlank() == true
                        viewModel.loadFavorites(query.orEmpty(), onQueryChangeSubmit)
                        onQueryChangeSubmit = true
                    },
                    onMenuCollapse = {
                        viewModel.loadFavorites()
                        emptySearch = true
                        requireActivity().hideKeyboard()
                        requireActivity().invalidateOptionsMenu()
                    },
                    onSubmitAvailable = true,
                    onQuerySubmit = { query ->
                        emptySearch = query?.isBlank() == true
                        viewModel.loadFavorites(query.orEmpty())
                    }
                )
            }

            /** Обработчики пунктов меню */
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().popBackStack()
                        true
                    }

                    R.id.action_order_settings -> {
                        showCustomOrderDialog()
                        true
                    }

                    R.id.action_search_settings -> {
                        showCustomSearchDialog()
                        true
                    }

                    R.id.menu_action_clear_cache -> {
                        alertDialog(
                            getString(R.string.question_remove),
                            getString(R.string.question_remove_all_history),
                            getString(android.R.string.ok),
                            getString(android.R.string.cancel),
                            onConfirm = {
                                viewModel.clearAllFavoriteHistory()
                            }
                        )
                        true
                    }

                    else -> false
                }

        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    /** Диалог настройки сортировки */
    private fun showCustomOrderDialog() {
        createCustomLayoutDialog(
            title = getString(R.string.search_order_settings),
            layout = R.layout.d_custom_order,
            cancelable = true,
            btnOkText = getString(android.R.string.ok),
            btnCancelText = getString(android.R.string.cancel),
            onConfirm = { viewModel.setOrder(currentOrder) },
            initView = {
                with(DCustomOrderBinding.bind(this)) {
                    rbLastTime.isVisible = true
                    swPriority.isVisible = false

                    val radioBtn = listOf(
                        rbLastTime to AppConstants.Order.LAST_TIME,
                        rbNone to AppConstants.Order.NONE,
                        rbTitle to AppConstants.Order.TITLE,
                        rbRatings to AppConstants.Order.RATINGS,
                        rbYearDesc to AppConstants.Order.YEAR_DESC,
                        rbYearAsc to AppConstants.Order.YEAR_ASC
                    )

                    currentOrder.takeIf { it.isNotBlank() }?.let {
                        when (it) {
                            AppConstants.Order.LAST_TIME -> rbLastTime.isChecked = true
                            AppConstants.Order.TITLE -> rbTitle.isChecked = true
                            AppConstants.Order.RATINGS -> rbRatings.isChecked = true
                            AppConstants.Order.YEAR_DESC -> rbYearDesc.isChecked = true
                            AppConstants.Order.YEAR_ASC -> rbYearAsc.isChecked = true
                            else -> rbLastTime.isChecked = true
                        }
                    } ?: kotlin.run { rbLastTime.isChecked = true }

                    radioBtn.forEach { (rb, orderString) ->
                        rb.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) currentOrder = orderString
                        }
                    }
                }
            })
    }

    /** Диалог настройки типа поиска */
    private fun showCustomSearchDialog() {
        createCustomLayoutDialog(
            title = getString(R.string.search_settings_title),
            layout = R.layout.d_custom_search,
            cancelable = true,
            btnOkText = getString(android.R.string.ok),
            btnCancelText = getString(android.R.string.cancel),
            onConfirm = { viewModel.setSearchType(searchType) },
            initView = {
                with(DCustomSearchBinding.bind(this)) {
                    val radioBtn = listOf(
                        rbTitle to AppConstants.SearchType.TITLE,
                        rbDirectors to AppConstants.SearchType.DIRECTORS,
                        rbActors to AppConstants.SearchType.ACTORS,
                        rbGenres to AppConstants.SearchType.GENRES
                    )

                    searchType.takeIf { it.isNotBlank() }?.let {
                        when (it) {
                            AppConstants.SearchType.TITLE -> rbTitle.isChecked = true
                            AppConstants.SearchType.DIRECTORS -> rbDirectors.isChecked = true
                            AppConstants.SearchType.ACTORS -> rbActors.isChecked = true
                            AppConstants.SearchType.GENRES -> rbGenres.isChecked = true
                            else -> rbTitle.isChecked = true
                        }
                    } ?: kotlin.run { rbTitle.isChecked = true }

                    radioBtn.forEach { (rb, orderString) ->
                        rb.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) searchType = orderString
                        }
                    }
                }
            })
    }

    /** Создаём адаптер и привязываем к RecyclerView */
    private fun initAdapters() {
        val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
        itemsAdapter = VideoItemsAdapter(baseUrl) { item ->
            findNavController().navigateSafely(
                FavoritesFragmentDirections.actionFavoritesFragmentToNavDetails(item.dbId)
            )
        }

        binding.rvFavoritesList.apply {
            adapter = itemsAdapter
            setHasFixedSize(true)
        }
    }

    /** Подписываемся на данные ViewModel */
    private fun observeData() {
        launchWhenCreated {
            viewModel.loading.collectLatest { loading ->
                binding.progressBar.isVisible = loading
            }
        }

        launchWhenCreated {
            viewModel.empty.collectLatest { empty ->
                hasSavedData = !empty
                requireActivity().invalidateOptionsMenu()
                binding.tvEmptyView.isVisible = empty
            }
        }

        launchWhenCreated {
            viewModel.favoriteDataFlow.collectLatest { movies ->
                itemsAdapter?.submitData(movies)
            }
        }

        launchWhenCreated {
            viewModel.order.collectLatest { order ->
                currentOrder = order
            }
        }
    }
}
