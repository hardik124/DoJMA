package com.csatimes.dojma;import android.app.Activity;import android.app.NotificationManager;import android.app.PendingIntent;import android.content.BroadcastReceiver;import android.content.Context;import android.content.Intent;import android.content.IntentFilter;import android.content.SharedPreferences;import android.graphics.BitmapFactory;import android.graphics.Color;import android.net.Uri;import android.os.Bundle;import android.preference.PreferenceManager;import android.support.annotation.Nullable;import android.support.customtabs.CustomTabsIntent;import android.support.design.widget.Snackbar;import android.support.v4.app.DialogFragment;import android.support.v4.app.Fragment;import android.support.v4.app.FragmentManager;import android.support.v4.content.ContextCompat;import android.support.v4.widget.SwipeRefreshLayout;import android.support.v7.widget.DefaultItemAnimator;import android.support.v7.widget.LinearLayoutManager;import android.support.v7.widget.RecyclerView;import android.support.v7.widget.SearchView;import android.support.v7.widget.helper.ItemTouchHelper;import android.util.Log;import android.view.LayoutInflater;import android.view.View;import android.view.ViewGroup;import android.widget.Button;import android.widget.EditText;import android.widget.ImageButton;import com.google.android.gms.analytics.HitBuilders;import com.google.android.gms.analytics.Tracker;import com.turingtechnologies.materialscrollbar.DateAndTimeIndicator;import com.turingtechnologies.materialscrollbar.DragScrollBar;import io.realm.Case;import io.realm.Realm;import io.realm.RealmConfiguration;import io.realm.RealmList;import io.realm.RealmResults;import io.realm.Sort;public class Herald extends Fragment implements SearchView.OnQueryTextListener,        SwipeRefreshLayout.OnRefreshListener, View.OnClickListener, FilterDialogFragment                .FilterDialogListener, SortDialogFragment.SortDialogListener {    public HeraldRV adapter;    ImageButton filterButton;    ImageButton sortButton;    boolean[] filterChecks = new boolean[3];    FragmentManager fm;    FilterDialogFragment dialogFragment;    private RecyclerView heraldRecyclerView;    private SharedPreferences preferences;    private SharedPreferences.Editor editor;    private Realm database;    private RealmConfiguration realmConfiguration;    private SearchView searchView;    private RealmResults<HeraldNewsItemFormat> results;    private RealmList<HeraldNewsItemFormat> resultsList;    private Tracker mTracker;    private SwipeRefreshLayout swipeRefreshLayout;    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {        @Override        public void onReceive(Context context, Intent intent) {            //Show snackbar to notify user that update check completed but            //no articles were downloaded.            //Also since Update service is fired every time in onStop of HomeActivity            //we add a check for swipelayout's refresh status            //If user had initiated refreshing only then show snackbar            //CODE FOLDED HERE            if (intent.getAction().compareTo(UpdateCheckerService.UPDATE_CHECK_OVER) == 0 &&                    swipeRefreshLayout.isRefreshing()) {                swipeRefreshLayout.setRefreshing(false);                try {                    Snackbar.make(heraldRecyclerView, "No updates available", Snackbar.LENGTH_SHORT)                            .show();                } catch (Exception e) {                    //Snackbar requires non null view,so if that fails we get the log                    Log.e("TAG", "RecyclerView is null");                }            } else if (intent.getAction().compareTo(UpdateCheckerService.DOWNLOAD_SUCCESS_ACTION) == 0) {                //cancel any pending intent and notification                Intent openHerald = new Intent(getContext(), HomeActivity.class);                PendingIntent.getActivity(getContext(), DHC.UPDATE_SERVICE_PENDING_INTENT_CODE, openHerald, PendingIntent.FLAG_CANCEL_CURRENT).cancel();                NotificationManager notificationManager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);                notificationManager.cancel(DHC.UPDATE_SERVICE_NOTIFICATION_CODE);                //update ui                try {                    if (resultsList.size() != 0 && adapter != null) {                        resultsList.clear();                        resultsList.addAll(database.where(HeraldNewsItemFormat.class).findAllSorted                                ("originalDate", Sort.DESCENDING));                        adapter.notifyDataSetChanged();                        try {                            Snackbar.make(heraldRecyclerView, "Articles were updated", Snackbar                                    .LENGTH_SHORT)                                    .show();                        } catch (Exception e) {                            //Snackbar requires non null view,so if that fails we get the log                            Log.e("TAG", "RecyclerView is null");                        }                    }                } catch (Exception e) {                    Log.e("TAG", "Exception");                } finally {                    swipeRefreshLayout.setRefreshing(false);                }            }            if (intent.getAction() == ImageUrlHandlerService.IMAGE_SERVICE_SUCCESS) {                adapter.notifyDataSetChanged();            }        }    };    private DragScrollBar dragScrollBar;    private Button issues;    private Button archives;    private CustomTabsIntent customTabsIntent;    private Context context;    private String issuu = "https://issuu.com/bitsherald";    public Herald() {        // Required empty public constructor    }    @Override    public void onCreate(@Nullable Bundle savedInstanceState) {        super.onCreate(savedInstanceState);        preferences = getContext().getSharedPreferences(DHC.USER_PREFERENCES,                Context.MODE_PRIVATE);        editor = preferences.edit();        Log.e("TAG", "onCreate Herald");        dialogFragment = FilterDialogFragment.newInstance("Select filters");        // SETS the target fragment for use later when sending results        dialogFragment.setTargetFragment(Herald.this, 300);        dialogFragment.setListener(this);        context = getContext();    }    @Override    public View onCreateView(LayoutInflater inflater, ViewGroup container,                             Bundle savedInstanceState) {        context = getContext();        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());        //Check if analytics is allowed by user        boolean sharedPrefAnalytics = sharedPref.getBoolean("pref_other_analytics", true);        if (sharedPrefAnalytics) {            AnalyticsApplication application = (AnalyticsApplication) getActivity().getApplication();            mTracker = application.getDefaultTracker();            mTracker.setScreenName("Herald");            mTracker.send(new HitBuilders.ScreenViewBuilder().build());        }        View V = inflater.inflate(R.layout.fragment_herald, container, false);        searchView = (SearchView) V.findViewById(R.id.herald_search_view);        EditText searchEditText = (EditText) searchView.findViewById(android.support.v7.appcompat.R.id.search_src_text);        heraldRecyclerView = (RecyclerView) V.findViewById(R.id.herald_recycler_view);        swipeRefreshLayout = (SwipeRefreshLayout) V.findViewById(R.id.swipe_refresh_container);        sortButton = (ImageButton) V.findViewById(R.id.herald_sort);        filterButton = (ImageButton) V.findViewById(R.id.herald_filter);        issues = (Button) V.findViewById(R.id.herald_issues_button);        archives = (Button) V.findViewById(R.id.herald_archives_button);        dragScrollBar = new DragScrollBar(getContext(), heraldRecyclerView, true)                .setDraggableFromAnywhere(true).setHandleColour(ContextCompat.getColor(getContext                        (), R.color.colorAccent)).addIndicator(new DateAndTimeIndicator(getContext                        (), true, true, true, false), true).setBarColour(ContextCompat.getColor                        (getContext(), R.color.grey500)).setHandleOffColour(ContextCompat.getColor                        (getContext(), R.color.colorAccent)).setTextColour(Color                        .WHITE);        searchEditText.setTextColor(Color.WHITE);        searchEditText.setHintTextColor(Color.parseColor("#44FFFFFF"));        searchView.setIconifiedByDefault(false);        searchView.setIconified(false);        searchView.clearFocus();        realmConfiguration = new RealmConfiguration.Builder(getContext())                .name                        (DHC.REALM_DOJMA_DATABASE).deleteRealmIfMigrationNeeded().build();        Realm.setDefaultConfiguration(realmConfiguration);        database = Realm.getDefaultInstance();        results = database.where(HeraldNewsItemFormat.class).equalTo("dismissed", false)                .findAllSorted("originalDate", Sort.DESCENDING);        resultsList = new RealmList<>();        resultsList.addAll(results);        adapter = new HeraldRV(getContext(), resultsList, database, getActivity());        adapter.setGoogleChromeInstalled(preferences.getBoolean(getString(R.string.SP_chrome_install_status), false));        heraldRecyclerView.setHasFixedSize(true);        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);        heraldRecyclerView.setLayoutManager(linearLayoutManager);        heraldRecyclerView.setItemAnimator(new DefaultItemAnimator());        heraldRecyclerView.setAdapter(adapter);        adapter.setRecyclerView(heraldRecyclerView);        ItemTouchHelper.Callback callback =                new SimpleItemTouchHelperCallback(adapter, heraldRecyclerView);        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);        touchHelper.attachToRecyclerView(heraldRecyclerView);        swipeRefreshLayout.setColorSchemeResources(R.color.amber500, R.color.blue500, R.color                .brown500, R.color.cyan500, R.color.deeporange500, R.color.deepPurple500, R.color.green500, R                .color.grey500, R.color.indigo500, R.color.lightblue500, R.color.lime500, R.color                .orange500, R.color.pink500, R.color.red500, R.color.teal500, R.color.violet500, R                .color.yellow500);        swipeRefreshLayout.setOnRefreshListener(this);        searchView.setOnQueryTextListener(this);        filterButton.setOnClickListener(this);        sortButton.setOnClickListener(this);        issues.setOnClickListener(this);        archives.setOnClickListener(this);        return V;    }    @Override    public void onResume() {        super.onResume();        searchView.clearFocus();        IntentFilter intf = new IntentFilter();        intf.addAction(UpdateCheckerService.DOWNLOAD_SUCCESS_ACTION);        intf.addAction(UpdateCheckerService.UPDATE_CHECK_OVER);        getActivity().registerReceiver(broadcastReceiver, intf);    }    public HeraldRV getAdapter() {        return adapter;    }    @Override    public void onPause() {        getActivity().unregisterReceiver(broadcastReceiver);        super.onPause();    }    @Override    public boolean onQueryTextSubmit(String query) {        results = database.where(HeraldNewsItemFormat.class).equalTo("dismissed", false)                .beginGroup()                .contains("title", query, Case.INSENSITIVE)                .or()                .contains("postID", query, Case.INSENSITIVE)                .or()                .contains("content", query, Case.INSENSITIVE)                .endGroup()                .findAllSorted("originalDate", Sort.DESCENDING);        resultsList.clear();        resultsList.addAll(results);        if (resultsList.size() != 0)            adapter.notifyDataSetChanged();        return true;    }    @Override    public boolean onQueryTextChange(String newText) {        resultsList.clear();        resultsList.addAll(database                .where(HeraldNewsItemFormat.class)                .equalTo("dismissed", false)                .beginGroup().contains("title", newText, Case.INSENSITIVE)                .or()                .contains("postID", newText, Case.INSENSITIVE)                .or()                .contains("content", newText, Case.INSENSITIVE)                .endGroup()                .findAllSorted("originalDate", Sort.DESCENDING));        if (resultsList.size() != 0)            adapter.notifyDataSetChanged();        return true;    }    @Override    public void onRefresh() {        if (!UpdateCheckerService.isInstanceCreated()) {            final Intent intent = new Intent(context, UpdateCheckerService.class);            getContext().startService(intent);        }    }    @Override    public void onClick(View view) {        int id = view.getId();        if (id == filterButton.getId()) {            fm = getFragmentManager();            dialogFragment.show(fm, "FilterDialogFragment");        } else if (id == sortButton.getId()) {            SortDialogFragment sdf = SortDialogFragment.newInstance("Sort using");            sdf.setTargetFragment(Herald.this, 301);            sdf.setListener(this);            sdf.show(getFragmentManager(), "SortDialogFragment");        } else if (id == issues.getId()) {            Intent intent = new Intent(context, CategoryListView.class);            startActivity(intent);        } else if (id == archives.getId()) {            {                Intent intent = new Intent((Intent.ACTION_SEND));                intent.putExtra(android.content.Intent.EXTRA_TEXT, "https://issuu.com/bitsherald");                Intent copy_intent = new Intent(context, CopyLinkBroadcastReceiver.class);                PendingIntent copy_pendingIntent = PendingIntent.getBroadcast(context, 0, copy_intent, PendingIntent.FLAG_UPDATE_CURRENT);                String copy_label = "Copy Link";                customTabsIntent = new CustomTabsIntent.Builder()                        .setShowTitle(true)                        .setToolbarColor(ContextCompat.getColor(context, R.color.colorPrimary))                        .setCloseButtonIcon(BitmapFactory.decodeResource(context.getResources(),                                R.drawable.ic_arrow_back_white_24dp))                        .addMenuItem(copy_label, copy_pendingIntent)                        .setStartAnimations(context, R.anim.slide_in_right, R.anim.fade_out)                        .setExitAnimations(context, R.anim.fade_in, R.anim.slide_out_right)                        .setSecondaryToolbarColor(ContextCompat.getColor(context, R.color.amber500))                        .addDefaultShareMenuItem()                        .enableUrlBarHiding()                        .build();                CustomTabActivityHelper.openCustomTab(getActivity(), customTabsIntent,                        Uri.parse(issuu),                        new CustomTabActivityHelper.CustomTabFallback() {                            @Override                            public void openUri(Activity activity, Uri uri) {                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);                                intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse(Intent.URI_ANDROID_APP_SCHEME + "//" + context.getPackageName()));                                context.startActivity(intent);                            }                        });            }        }    }    //Methods from FilterDialogFragment    @Override    public void onDialogPositiveClick(DialogFragment dialog) {        String[] options = getResources().getStringArray(R.array.filter_options);        boolean[] states = new boolean[options.length];        for (int i = 0; i < states.length; i++) {            states[i] = preferences.getBoolean(DHC.FILTER_SUFFIX + options[i], false);        }        resultsList.clear();        resultsList.addAll(database.where(HeraldNewsItemFormat.class).equalTo("fav", states[0])                .equalTo("dismissed", states[1]).findAllSorted("updateDate", Sort.DESCENDING));        adapter.notifyDataSetChanged();        if (resultsList.size() == 0) dragScrollBar.setVisibility(View.GONE);        else if (resultsList.size() != 0) dragScrollBar.setVisibility(View.VISIBLE);    }    @Override    public void onFilterDialogNegativeClick(DialogFragment dialog) {        Log.e("TAG", "herald");    }    @Override    public void onSortDialogPositiveClick() {        resultsList.clear();        String[] options = getResources().getStringArray(R.array.sort_options);        boolean[] states = new boolean[options.length];        for (int i = 0; i < states.length; i++) {            states[i] = preferences.getBoolean(DHC.SORT_SUFFIX + options[i], false);        }        if (states[0]) {            resultsList.addAll(database.where(HeraldNewsItemFormat.class).findAllSorted("title",                    Sort.ASCENDING));            adapter.notifyDataSetChanged();        } else if (states[1]) {            resultsList.addAll(database.where(HeraldNewsItemFormat.class).findAllSorted                    ("originalDate",                            Sort.ASCENDING));            adapter.notifyDataSetChanged();        } else if (states[2]) {            resultsList.addAll(database.where(HeraldNewsItemFormat.class).findAllSorted                    ("updateDate", Sort.ASCENDING));            adapter.notifyDataSetChanged();        }        if (resultsList.size() == 0) dragScrollBar.setVisibility(View.GONE);        else if (resultsList.size() != 0) dragScrollBar.setVisibility(View.VISIBLE);    }    @Override    public void onSortDialogNegativeClick(DialogFragment dialog) {    }}