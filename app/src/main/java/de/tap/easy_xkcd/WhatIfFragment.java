package de.tap.easy_xkcd;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.tap.xkcd_reader.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class WhatIfFragment extends android.support.v4.app.Fragment {

    public static ArrayList<String> mTitles = new ArrayList<>();
    private static ArrayList<String> mImgs = new ArrayList<>();
    public static RecyclerView rv;
    private MenuItem searchMenuItem;
    public static RVAdapter adapter;
    private static WhatIfFragment instance;
    public static boolean newIntent;
    private FloatingActionButton fab;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.whatif_recycler, container, false);

        setHasOptionsMenu(true);

        fab = (FloatingActionButton) v.findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openRandomWhatIf();
            }
        });
        rv = (RecyclerView) v.findViewById(R.id.rv);
        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        rv.setLayoutManager(llm);
        rv.setHasFixedSize(false);
        rv.addOnScrollListener(new CustomOnScrollListener());

        instance = this;

        ((MainActivity) getActivity()).mFab.setVisibility(View.GONE);

        new DisplayOverview().execute();

        return v;
    }

    private class DisplayOverview extends AsyncTask<Void, Void, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(getActivity());
            progress.setMessage(getResources().getString(R.string.loading_articles));
            progress.setIndeterminate(true);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... dummy) {
            mTitles.clear();
            mImgs.clear();
            try {
                Document doc = Jsoup.connect("https://what-if.xkcd.com/archive/")
                        .userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1700.19 Safari/537.36")
                        .get();
                Log.e("Info", "doc loaded");
                Elements titles = doc.select("h1");
                Elements imagelinks = doc.select("img.archive-image");

                for (Element title : titles) {
                    mTitles.add(title.text());
                }
                Log.e("Info", "titles");
                for (Element image : imagelinks) {
                    mImgs.add(image.absUrl("src"));
                }
                Log.e("Info", "imgs");

                Collections.reverse(mTitles);
                Collections.reverse(mImgs);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            PrefHelper.setNewestWhatif(mTitles.size());
            if (PrefHelper.hideRead()) {
                ArrayList<String> titleUnread = new ArrayList<>();
                ArrayList<String> imgUnread = new ArrayList<>();
                for (int i = 0; i < mTitles.size(); i++) {
                    if (!PrefHelper.checkRead(mTitles.size() - i)) {
                        titleUnread.add(mTitles.get(i));
                        imgUnread.add(mImgs.get(i));
                    }
                }
                adapter = new RVAdapter(titleUnread, imgUnread);
                rv.setAdapter(adapter);
                rv.scrollToPosition(titleUnread.size() - PrefHelper.getLastWhatIf());
            } else {
                adapter = new RVAdapter(mTitles, mImgs);
                rv.setAdapter(adapter);
                rv.scrollToPosition(mTitles.size() - PrefHelper.getLastWhatIf());
            }
            progress.dismiss();
            Toolbar toolbar = ((MainActivity)getActivity()).toolbar;
            if (toolbar.getAlpha()==0) {
                toolbar.setTranslationY(-300);
                toolbar.animate().setDuration(300).translationY(0).alpha(1);
                View view;
                for (int i = 0; i<toolbar.getChildCount(); i++) {
                    view = toolbar.getChildAt(i);
                    view.setTranslationY(-300);
                    view.animate().setStartDelay(50*(i+1)).setDuration(70*(i+1)).translationY(0);
                }
            }

            if (newIntent) {
                Intent intent = new Intent(getActivity(), WhatIfActivity.class);
                startActivity(intent);
                newIntent = false;
            }
        }

    }

    public class RVAdapter extends RecyclerView.Adapter<RVAdapter.ComicViewHolder> {
        private int lastPosition = 0;
        public ArrayList<String> titles;
        private ArrayList<String> imgs;

        @Override
        public int getItemCount() {
            return titles.size();
        }

        public RVAdapter(ArrayList<String> t, ArrayList<String> i) {
            this.titles = t;
            this.imgs = i;
        }

        @Override
        public ComicViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.whatif_overview, viewGroup, false);
            v.setOnClickListener(new CustomOnClickListener());
            v.setOnLongClickListener(new CustomOnLongClickListener());
            return new ComicViewHolder(v);
        }

        @Override
        public void onBindViewHolder(final ComicViewHolder comicViewHolder, int i) {
            comicViewHolder.articleTitle.setText(titles.get(i));

            String title = titles.get(i);
            int n = mTitles.size() - mTitles.indexOf(title);

            if (PrefHelper.checkRead(n)) {
                comicViewHolder.articleTitle.setTextColor(getResources().getColor(R.color.Read));
            } else {
                comicViewHolder.articleTitle.setTextColor(getResources().getColor(android.R.color.tertiary_text_light));
            }

            if (titles.get(i).equals("Jupiter Descending")) {
                comicViewHolder.thumbnail.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.mipmap.jupiter_descending));
                return;
            }
            if (titles.get(i).equals("Jupiter Submarine")) {
                comicViewHolder.thumbnail.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.mipmap.jupiter_submarine));
                return;
            }
            if (titles.get(i).equals("New Horizons")) {
                comicViewHolder.thumbnail.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.mipmap.new_horizons));
                return;
            }
            /*Glide.with(getActivity())
                    .load(imgs.get(i))
                    .asBitmap()
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                            comicViewHolder.thumbnail.setImageBitmap(resource);
                        }
                    });*/

            Glide.with(getActivity())
                    .load(mImgs.get(i))
                    .into(comicViewHolder.thumbnail);

            /*if (mTitles.size() == titles.size()) {
                setAnimation(comicViewHolder.cv, i);
            }*/
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public class ComicViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView articleTitle;
            ImageView thumbnail;

            ComicViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.cv);
                articleTitle = (TextView) itemView.findViewById(R.id.article_title);
                thumbnail = (ImageView) itemView.findViewById(R.id.thumbnail);
            }
        }

        private void setAnimation(View viewToAnimate, int position) {
            // If the bound view wasn't previously displayed on screen, it's animated
            if (position > lastPosition) {
                Animation animation = AnimationUtils.loadAnimation(getActivity(), android.R.anim.slide_in_left);
                viewToAnimate.startAnimation(animation);
                lastPosition = position;
            }
        }
    }

    class CustomOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (!isOnline()) {
                Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
                return;
            }
            int pos = rv.getChildAdapterPosition(v);
            Intent intent = new Intent(getActivity(), WhatIfActivity.class);
            String title = adapter.titles.get(pos);
            int n = mTitles.size() - mTitles.indexOf(title);
            WhatIfActivity.WhatIfIndex = n;
            startActivity(intent);
            Log.d("index", String.valueOf(n));

            PrefHelper.setLastWhatIf(n);
            PrefHelper.setWhatifRead(String.valueOf(n));
            if (searchMenuItem.isActionViewExpanded()) {
                searchMenuItem.collapseActionView();
                rv.scrollToPosition(mTitles.size() - n);
            }
        }
    }

    private void openRandomWhatIf() {
        if (isOnline()) {
            Random mRand = new Random();
            int number = mRand.nextInt(adapter.titles.size());
            Intent intent = new Intent(getActivity(), WhatIfActivity.class);
            String title = adapter.titles.get(number);
            int n = mTitles.size() - mTitles.indexOf(title);
            WhatIfActivity.WhatIfIndex = n;
            startActivity(intent);
            PrefHelper.setLastWhatIf(n);
            PrefHelper.setWhatifRead(String.valueOf(n));
        } else {
            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
        }
    }

    class CustomOnLongClickListener implements  View.OnLongClickListener {
        @Override
        public boolean onLongClick(View v) {
            final View view = v;
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setItems(R.array.card_long_click, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    int pos;
                    int n;
                    String title;
                    switch (which) {
                        case 0:
                            pos = rv.getChildAdapterPosition(view);
                            title = adapter.titles.get(pos);
                            n = mTitles.size() - mTitles.indexOf(title);
                            Intent share = new Intent(Intent.ACTION_SEND);
                            share.setType("text/plain");
                            share.putExtra(Intent.EXTRA_SUBJECT, "What if: " + title);
                            share.putExtra(Intent.EXTRA_TEXT, "http://what-if.xkcd.com/" + String.valueOf(n));
                            startActivity(share);
                            break;
                        case 1:
                            pos = rv.getChildAdapterPosition(view);
                            title = adapter.titles.get(pos);
                            n = mTitles.size() - mTitles.indexOf(title);
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://what-if.xkcd.com/" + String.valueOf(n)));
                            startActivity(intent);
                            break;
                    }
                }
            });
            AlertDialog alert = builder.create();
            alert.show();
           return true;
        }
    }

    class CustomOnScrollListener extends RecyclerView.OnScrollListener {
        int scrollDist = 0;
        boolean isVisible = true;
        static final float MINIMUM = 25;
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            if (isVisible && scrollDist > MINIMUM) {
                Resources r = getActivity().getResources();
                float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, r.getDisplayMetrics());
                fab.animate().translationY(fab.getHeight() + px ).setInterpolator(new AccelerateInterpolator(2)).start();
                scrollDist = 0;
                isVisible = false;
            }
            else if (!isVisible && scrollDist < -MINIMUM) {
                fab.animate().translationY(0).setInterpolator(new DecelerateInterpolator(2)).start();
                scrollDist = 0;
                isVisible = true;
            }
            if ((isVisible && dy > 0) || (!isVisible && dy < 0)) {
                scrollDist += dy;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_unread:
                PrefHelper.setAllUnread();
                adapter.notifyDataSetChanged();
                return true;
            case R.id.action_hide_read:
                item.setChecked(!item.isChecked());
                PrefHelper.setHideRead(item.isChecked());
                if (item.isChecked()) {
                    ArrayList<String> titleUnread = new ArrayList<>();
                    ArrayList<String> imgUnread = new ArrayList<>();
                    for (int i = 0; i < mTitles.size(); i++) {
                        if (!PrefHelper.checkRead(mTitles.size() - i)) {
                            titleUnread.add(mTitles.get(i));
                            imgUnread.add(mImgs.get(i));
                        }
                    }
                    adapter = new RVAdapter(titleUnread, imgUnread);
                    rv.setAdapter(adapter);
                    rv.scrollToPosition(titleUnread.size() - PrefHelper.getLastWhatIf());
                } else {
                    adapter = new RVAdapter(mTitles, mImgs);
                    rv.setAdapter(adapter);
                    rv.scrollToPosition(mTitles.size() - PrefHelper.getLastWhatIf());
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void updateRv() {
        if (PrefHelper.hideRead()) {
            ArrayList<String> titleUnread = new ArrayList<>();
            ArrayList<String> imgUnread = new ArrayList<>();
            for (int i = 0; i < mTitles.size(); i++) {
                if (!PrefHelper.checkRead(mTitles.size() - i)) {
                    titleUnread.add(mTitles.get(i));
                    imgUnread.add(mImgs.get(i));
                }
            }
            adapter = new RVAdapter(titleUnread, imgUnread);
            rv.setAdapter(adapter);
            rv.scrollToPosition(titleUnread.size() - PrefHelper.getLastWhatIf());
        } else {
            adapter = new RVAdapter(mTitles, mImgs);
            rv.setAdapter(adapter);
            rv.scrollToPosition(mTitles.size() - PrefHelper.getLastWhatIf());
        }
    }

    public static WhatIfFragment getInstance() {
        return instance;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        for (int i = 0; i < menu.size()-2; i++) {
            menu.getItem(i).setVisible(false);
        }

        menu.findItem(R.id.action_unread).setVisible(true);
        menu.findItem(R.id.action_hide_read).setVisible(true);
        menu.findItem(R.id.action_hide_read).setChecked(PrefHelper.hideRead());

        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        final SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
        searchView.setIconifiedByDefault(false);
        searchMenuItem = menu.findItem(R.id.action_search);
        searchMenuItem.setVisible(true);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                ArrayList<String> titleResults = new ArrayList<>();
                ArrayList<String> imgResults = new ArrayList<>();
                for (int i = 0; i < mTitles.size(); i++) {
                    if (mTitles.get(i).toLowerCase().contains(newText.toLowerCase().trim())) {
                        titleResults.add(mTitles.get(i));
                        imgResults.add(mImgs.get(i));
                    }
                }
                adapter = new RVAdapter(titleResults, imgResults);
                rv.setAdapter(adapter);
                return false;
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchMenuItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                View view = getActivity().getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(view, 0);
                }
                searchView.requestFocus();


                fab.setVisibility(View.INVISIBLE);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                View view = getActivity().getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
                adapter = new RVAdapter(mTitles, mImgs);
                rv.setAdapter(adapter);

                fab.setVisibility(View.VISIBLE);
                fab.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.grow));
                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    private boolean isOnline() {
        //Checks if the device is currently online
        ConnectivityManager cm =
                (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();

    }
}
