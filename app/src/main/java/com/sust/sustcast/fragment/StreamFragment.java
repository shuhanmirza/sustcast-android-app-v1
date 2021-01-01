package com.sust.sustcast.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.crashlytics.android.Crashlytics;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sust.sustcast.R;
import com.sust.sustcast.data.IceUrl;
import com.sust.sustcast.utils.ExoHelper;
import com.sust.sustcast.utils.LoadBalancingUtil;
import com.sust.sustcast.utils.NetworkInfoUtility;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.Unbinder;

import static com.sust.sustcast.data.Constants.CHECKNET;
import static com.sust.sustcast.data.Constants.SERVEROFF;


public class StreamFragment extends Fragment implements Player.EventListener {

    private static final String TAG = "StreamFragment";
    ChildEventListener cListener;
    Unbinder unbinder;
    Button bPlay;
    TextView tvPlaying;
    ExoHelper exoHelper;
    private boolean isPlaying;
    private DatabaseReference rootRef;
    private DatabaseReference songReference;
    private DatabaseReference urlRef;
    private List<IceUrl> iceUrlList;
    private String title;
    private String token;
    private BroadcastReceiver receiver;
    public String PLAY = "com.sust.sustcast.PLAY";
    public String PAUSE = "com.sust.sustcast.PAUSE";

    public StreamFragment() {
    }

    public static StreamFragment newInstance() {
        StreamFragment fragment = new StreamFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {

        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_stream, container, false);
        tvPlaying = rootView.findViewById(R.id.tv_track);
        tvPlaying.setText(rootView.getContext().getString(R.string.metadata_loading));
        NetworkInfoUtility networkInfoUtility = new NetworkInfoUtility();
        boolean net = networkInfoUtility.isNetWorkAvailableNow(getContext());

        bPlay = rootView.findViewById(R.id.button_stream);
        unbinder = ButterKnife.bind(this, rootView);

        exoHelper = new ExoHelper(getContext(), new Player.EventListener() {
            @Override
            public void onPlayerError(ExoPlaybackException error) {
                Log.i(TAG, "NETWORKERROR");

                if (!(getContext() == null))
                {
                    Toast.makeText(getContext(), SERVEROFF, Toast.LENGTH_LONG).show();
                }

                else
                {
                    Log.d(TAG, "onPlayerError: " + "Context is null");
                }


                exoHelper.ToggleButton(false);
                exoHelper.StopNotification();
                Crashlytics.logException(error);

            }

        }, bPlay, "Streaming");

        isPlaying = true;
        setButton();
        if (!net) {
            exoHelper.ToggleButton(false);
            Toast.makeText(rootView.getContext(), CHECKNET, Toast.LENGTH_LONG).show();
            tvPlaying.setText(R.string.server_off);
        }
        rootRef = FirebaseDatabase.getInstance().getReference(); //root database reference
        setServerUrlListners();
        setMetaDataListeners();
        startRadioOnCreate();

        return rootView;
    }

    private void startRadioOnCreate() {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (iceUrlList.size() > 0) {
                    Log.d(TAG, "start radio on create");
                    exoHelper.startExo(LoadBalancingUtil.selectIceCastSource(iceUrlList).getUrl());
                } else {
                    startRadioOnCreate();
                }
            }
        }, 1000);
    }

    private void setMetaDataListeners() {
        songReference = rootRef.child("song");
        songReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                title = dataSnapshot.child("title_show").getValue(String.class);
                tvPlaying.setText(title);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void setServerUrlListners() {
        iceUrlList = new ArrayList<>();

        urlRef = rootRef.child("IcecastServer");
        cListener = urlRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { // read each child and compute load
                IceUrl iceUrl = dataSnapshot.getValue(IceUrl.class);
                iceUrlList.add(iceUrl);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void setButton() {
        bPlay.setOnClickListener(view -> {
            Log.d(TAG, "setButton: isplaying -> " + isPlaying);

            if (isPlaying) {
                exoHelper.stopExo();
            } else {
                exoHelper.startExo(LoadBalancingUtil.selectIceCastSource(iceUrlList).getUrl());
            }
            isPlaying = !isPlaying;
        });


    }


    @Override
    public void onStart() {
        super.onStart();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(PAUSE);
        intentFilter.addAction(PLAY);

        if (receiver == null) {
            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {

                    if (!(intent.getAction() == null)) {
                        if (intent.getAction().equals(PAUSE)) {
                            Log.d(TAG, "onReceive: " + "Paused");
                            exoHelper.ToggleButton(false);

                        } else if (intent.getAction().equals(PLAY)) {
                            Log.d(TAG, "onReceive: " + "Playing");
                            exoHelper.ToggleButton(true);
                        }
                    } else {
                        Log.d(TAG, "onReceive: " + "Nothing received!");
                    }


                }
            };

            getActivity().registerReceiver(receiver, intentFilter);

        } else {
            Log.d(TAG, "onStart: " + "Receiver already registered");
        }


    }

    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
        urlRef.removeEventListener(cListener);
        exoHelper.stopExo();


        try {
            if (receiver != null) {
                getActivity().unregisterReceiver(receiver);
            }
        } catch (Exception exception) {
            Log.d(TAG, "onDestroyView: " + "Exception!!");
            Crashlytics.logException(exception);
        }

    }


}