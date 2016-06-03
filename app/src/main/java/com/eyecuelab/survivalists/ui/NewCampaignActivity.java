package com.eyecuelab.survivalists.ui;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.percent.PercentRelativeLayout;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.eyecuelab.survivalists.Constants;
import com.eyecuelab.survivalists.R;
import com.eyecuelab.survivalists.adapters.InvitationAdapter;
import com.eyecuelab.survivalists.adapters.PlayerAdapter;
import com.eyecuelab.survivalists.models.Character;
import com.eyecuelab.survivalists.models.Item;
import com.eyecuelab.survivalists.models.SafeHouse;
import com.eyecuelab.survivalists.models.Weapon;
import com.eyecuelab.survivalists.models.User;
import com.eyecuelab.survivalists.util.CampaignEndAlarmReceiver;
import com.eyecuelab.survivalists.util.MatchUpdateListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.InvitationBuffer;
import com.google.android.gms.games.multiplayer.Invitations;
import com.google.android.gms.games.multiplayer.Participant;
import com.google.android.gms.games.multiplayer.turnbased.OnTurnBasedMatchUpdateReceivedListener;
import com.google.android.gms.games.multiplayer.turnbased.TurnBasedMatch;
import com.google.android.gms.games.multiplayer.turnbased.TurnBasedMatchConfig;
import com.google.android.gms.games.multiplayer.turnbased.TurnBasedMultiplayer;
import com.google.example.games.basegameutils.BaseGameActivity;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;

import butterknife.Bind;
import butterknife.ButterKnife;

public class NewCampaignActivity extends BaseGameActivity implements View.OnClickListener {
    private int mDifficultyLevel;
    private int mCampaignLength;
    private int mPartySize = 1;
    private int mLastSafeHouseId;
    private int mNextSafeHouseId;
    private boolean mConfirmingSettings = true;
    private String mDifficultyDescription;
    private String mCurrentMatchId;
    private String mCurrentPlayerId;
    private ArrayList<String> difficultyDescriptions = new ArrayList<>();
    private ArrayList<String> invitedPlayers = new ArrayList<>();
    Integer[] campaignDuration = {15, 30, 45};
    Integer[] defaultDailyGoal = {5000, 7000, 10000};

    private Context mContext;
    private ListView mInvitePlayersListView;
    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;
    private SafeHouse mPriorSafehouse;
    private SafeHouse mNextSafehouse;

    private GoogleApiClient mGoogleApiClient;
    private TurnBasedMatch mCurrentMatch;
    private byte[] turnData;
    final int WAITING_ROOM_TAG = 1;
    public static final String RECEIVE_UPDATE_FROM_INVITATION = "com.eyecuelab.survivalists.ui.RECEIVE_UPDATE_FROM_INVITATION";
    public static final String RECEIVE_UPDATE_FROM_MATCH = "com.eyecuelab.survivalists.ui.RECEIVE_UPDATE_FROM_MATCH";
    private ArrayList<Weapon> allWeapons;
    private ArrayList<Item> allFood;
    private ArrayList<Item> allMedicine;


    @Bind(R.id.difficultySeekBar) SeekBar difficultySeekBar;
    @Bind(R.id.campaignLengthSeekBar) SeekBar lengthSeekBar;
    @Bind(R.id.partySizeSeekBar) SeekBar partySeekBar;
    @Bind(R.id.difficultyDescription) TextView difficultyTextView;
    @Bind(R.id.lengthText) TextView lengthTextView;
    @Bind(R.id.invitePlayersListView) ListView invitePlayerListView;
    @Bind(R.id.infoListView) ListView infoListView;
    @Bind(R.id.confirmationButton) Button confirmationButton;
    @Bind(R.id.settingsField) PercentRelativeLayout settingsLayout;
    @Bind(R.id.settingsConfirmedSection) PercentRelativeLayout settingConfirmationLayout;
    @Bind(R.id.infoSection) PercentRelativeLayout generalInfoLayout;
    @Bind(R.id.teamBuildingSection) PercentRelativeLayout playerInvitationLayout;
    @Bind(R.id.difficultyConfirmedText) TextView difficultyConfirmedTextView;
    @Bind(R.id.lengthConfirmedText) TextView lengthConfirmedTextView;
    @Bind(R.id.partySizeText) TextView partyTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Firebase.setAndroidContext(this);
        allWeapons = new ArrayList<>();
        allMedicine = new ArrayList<>();
        allFood = new ArrayList<>();

        setFullScreen();

        //set content view AFTER ABOVE sequence (to avoid crash)
        setContentView(R.layout.activity_new_campaign);

        ButterKnife.bind(this);
        mContext = this;

        //Create Shared Preferences
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mEditor = mSharedPreferences.edit();

        confirmationButton.setOnClickListener(this);

        difficultyDescriptions.add("Walk in the park");
        difficultyDescriptions.add("Walk the line");
        difficultyDescriptions.add("Walk the talk");

        mCampaignLength = campaignDuration[0];
        mDifficultyLevel = 0;
        mDifficultyDescription = difficultyDescriptions.get(0);

        initiateSeekBars();

        mGoogleApiClient = getApiClient();

        ArrayAdapter<String> infoAdapter = new ArrayAdapter<>(NewCampaignActivity.this, R.layout.info_list_item, getResources().getStringArray(R.array.difficultyDescriptions));
        infoListView.setAdapter(infoAdapter);

        //Create Shared Preferences
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mEditor = mSharedPreferences.edit();

        int navigationFlag = getIntent().getIntExtra("statusTag", -1);
        if (navigationFlag == Constants.JOIN_CAMPAIGN_INTENT) {
            setupJoinMatchesUi();
        }
        Firebase itemRef = new Firebase(Constants.FIREBASE_URL_ITEMS);

        itemRef.child("weapons").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for(DataSnapshot child: dataSnapshot.getChildren()) {
                    Weapon weapon = child.getValue(Weapon.class);
                    allWeapons.add(weapon);
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });

        itemRef.child("food").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for(DataSnapshot child: dataSnapshot.getChildren()) {
                    Item item = child.getValue(Item.class);
                    allFood.add(item);
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });

        itemRef.child("medicine").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for(DataSnapshot child: dataSnapshot.getChildren()) {
                    Item item = child.getValue(Item.class);
                    allMedicine.add(item);
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });

        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RECEIVE_UPDATE_FROM_INVITATION);
        broadcastManager.registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setFullScreen();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.confirmationButton:
                if (mConfirmingSettings) {
                    saveCampaignSettings();
                    loadAvailablePlayers();
                } else if (mPartySize == invitedPlayers.size()){
                    Toast.makeText(NewCampaignActivity.this, "Invitations sent", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(NewCampaignActivity.this, "Waiting for " + mPartySize + " players to join.", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    public void setFullScreen() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    public void loadAvailablePlayers() {
        mConfirmingSettings = false;
        //Start the invitaiton UI
        final int MIN_OPPONENTS = 1;
        Intent intent = Games.TurnBasedMultiplayer.getSelectOpponentsIntent(mGoogleApiClient, MIN_OPPONENTS, mPartySize, false);
        startActivityForResult(intent, WAITING_ROOM_TAG);

        settingsLayout.setVisibility(View.GONE);
        settingConfirmationLayout.setVisibility(View.VISIBLE);
        generalInfoLayout.setVisibility(View.GONE);
        playerInvitationLayout.setVisibility(View.VISIBLE);

        int remainingInvites = mPartySize - invitedPlayers.size();
        confirmationButton.setText(remainingInvites + " invitations remaining...");

        difficultyConfirmedTextView.setText("Difficulty: " + mDifficultyDescription);
        lengthConfirmedTextView.setText("Length: " + mCampaignLength + " Days");
    }

    public void initiateSeekBars() {
        difficultySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressTotal = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressTotal = progress;
                difficultyTextView.setText(difficultyDescriptions.get(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mDifficultyLevel = defaultDailyGoal[progressTotal];
                mDifficultyDescription = difficultyDescriptions.get(progressTotal);
            }
        });

        lengthSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressTotal = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lengthTextView.setText(campaignDuration[progress] + " Days");
                progressTotal = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mCampaignLength = campaignDuration[progressTotal];
            }
        });

        partySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressTotal = 1;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int currentCount = progress + 2;
                partyTextView.setText(currentCount + " Players");
                progressTotal = progress + 1;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mPartySize = progressTotal;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        setFullScreen();

        //Back from inviting players
        if (requestCode == WAITING_ROOM_TAG && resultCode == Activity.RESULT_OK) {
            invitedPlayers = data.getStringArrayListExtra(Games.EXTRA_PLAYER_IDS);
            TurnBasedMatchConfig turnBasedMatchConfig = TurnBasedMatchConfig.builder()
                    .addInvitedPlayers(invitedPlayers)
                    .build();

            Games.TurnBasedMultiplayer
                    .createMatch(mGoogleApiClient, turnBasedMatchConfig)
                    .setResultCallback(new ResultCallback<TurnBasedMultiplayer.InitiateMatchResult>() {
                        @Override
                        public void onResult(@NonNull TurnBasedMultiplayer.InitiateMatchResult result) {
                            mCurrentMatch = result.getMatch();
                            loadMatch(result.getMatch().getMatchId());
                            initializeWaitingRoomUi();
                        }
                    });
        }
    }

    public void initializeWaitingRoomUi() {
        settingsLayout.setVisibility(View.GONE);
        settingConfirmationLayout.setVisibility(View.VISIBLE);
        generalInfoLayout.setVisibility(View.GONE);
        playerInvitationLayout.setVisibility(View.VISIBLE);

        //TODO: Need to pull these parameters from firebase or shared preferences
//        difficultyConfirmedTextView.setText("Difficulty: " + difficultyDescriptions.get(mDifficultyLevel));
//        lengthConfirmedTextView.setText("Length: " + lengths.get(mCampaignLength) + " Days");
        confirmationButton.setText("Waiting for players to join...");

        if (mCurrentMatch != null) {
            ArrayList<String> playerIds = mCurrentMatch.getParticipantIds();
            ArrayList<User> matchUsers = new ArrayList<>();

            for (int i = 1; i < playerIds.size(); i++) {
                String playerId = playerIds.get(i);
                Participant participant = mCurrentMatch.getParticipant(playerId);

                String UID = participant.getParticipantId();
                String displayName = participant.getDisplayName();
                Uri imageUri = participant.getIconImageUri();

                User currentUser = new User(UID, displayName, mCurrentMatchId, imageUri);
                matchUsers.add(currentUser);

            }

            invitePlayerListView.setAdapter(new PlayerAdapter(this, matchUsers, R.layout.player_list_item));
        }
    }

    public void setupJoinMatchesUi() {
        settingsLayout.setVisibility(View.GONE);
        settingConfirmationLayout.setVisibility(View.VISIBLE);
        generalInfoLayout.setVisibility(View.GONE);
        playerInvitationLayout.setVisibility(View.VISIBLE);

        //TODO: Need to pull these parameters from firebase or shared preferences
//        difficultyConfirmedTextView.setText("Difficulty: " + difficultyDescriptions.get(mDifficultyLevel));
//        lengthConfirmedTextView.setText("Length: " + lengths.get(mCampaignLength) + " Days");
        confirmationButton.setText("Waiting for players to join...");

        Games.Invitations.loadInvitations(mGoogleApiClient).setResultCallback(new ResultCallback<Invitations.LoadInvitationsResult>() {
            @Override
            public void onResult(@NonNull Invitations.LoadInvitationsResult loadInvitationsResult) {
                InvitationBuffer invitationBuffer = loadInvitationsResult.getInvitations();
                ArrayList<Participant> invitationParticipants = new ArrayList<>();
                ArrayList<Invitation> invitationArrayList = new ArrayList<>();
                for (int i = 0; i < invitationBuffer.getCount(); i++) {
                    Invitation invitation = invitationBuffer.get(i);
                    invitationArrayList.add(invitation);
                    Participant inviter = invitation.getInviter();
                    invitationParticipants.add(inviter);
                }
                invitePlayerListView.setAdapter(new InvitationAdapter(NewCampaignActivity.this, invitationParticipants, invitationArrayList, R.layout.invitation_list_item, mGoogleApiClient));
            }
        });
    }

    public void loadMatch(String matchId) {
        mCurrentMatchId = matchId;

        mNextSafeHouseId = mSharedPreferences.getInt(Constants.PREFERENCES_NEXT_SAFEHOUSE_ID, 1);
        mLastSafeHouseId = mSharedPreferences.getInt(Constants.PREFERENCES_LAST_SAFEHOUSE_ID, 0);

        Games.TurnBasedMultiplayer.loadMatch(mGoogleApiClient, mCurrentMatchId).setResultCallback(new ResultCallback<TurnBasedMultiplayer.LoadMatchResult>() {
            @Override
            public void onResult(@NonNull TurnBasedMultiplayer.LoadMatchResult result) {
                mCurrentMatch = result.getMatch();
                takeTurn();
                mEditor.putString(Constants.PREFERENCES_MATCH_ID, mCurrentMatchId);
                mEditor.commit();
            }
        });
    }

    public void takeTurn() {
        turnData = mCurrentMatch.getData();
        mCurrentPlayerId = Games.Players.getCurrentPlayerId(mGoogleApiClient);

        //First turn
        if (turnData == null) {
            mCurrentMatchId = mCurrentMatch.getMatchId();
            ArrayList<String> wholeParty = invitedPlayers;
            if (wholeParty != null) {
                wholeParty.add(mCurrentPlayerId);
            }

            mEditor.putString(Constants.PREFERENCES_MATCH_ID, mCurrentMatchId);
            mEditor.putInt(Constants.PREFERENCES_LAST_SAFEHOUSE_ID, 0);
            mEditor.putInt(Constants.PREFERENCES_NEXT_SAFEHOUSE_ID, 1);
            mEditor.commit();

            Firebase teamFirebaseRef = new Firebase(Constants.FIREBASE_URL_TEAM + "/" + "").child(mCurrentMatchId);
            teamFirebaseRef.child("matchStart").setValue(mCurrentMatch.getCreationTimestamp());
            teamFirebaseRef.child("matchDuration").setValue(mCampaignLength);
            teamFirebaseRef.child("difficultyLevel").setValue(mDifficultyLevel);
            teamFirebaseRef.child("lastSafehouseId").setValue(0);
            teamFirebaseRef.child("nextSafehouseId").setValue(1);

            Firebase playerFirebase = teamFirebaseRef.child("players");
            if (wholeParty != null) {
                for (int i = 0; i < wholeParty.size(); i++) {
                    playerFirebase
                            .child("p_" + (i + 1))
                            .setValue(wholeParty.get(i));
                }
            }

            Firebase mUserFirebaseRef = new Firebase(Constants.FIREBASE_URL_USERS + "/" + mCurrentPlayerId + "/");
            mUserFirebaseRef.child("teamId").setValue(mCurrentMatchId);
            mUserFirebaseRef.child("joinedMatch").setValue(true);
            createCampaign(mCampaignLength);
            saveSafehouse();
            turnData = new byte[1];

            //Take as many turns as there are players, to invite all players at once
            for (int i = 0; i < mCurrentMatch.getParticipantIds().size(); i++) {
                String nextPlayer = mCurrentMatch.getParticipantIds().get(i);
                Games.TurnBasedMultiplayer.takeTurn(mGoogleApiClient, mCurrentMatchId, turnData, nextPlayer);
            }
            assignStarterInventory();
            assignRandomCharacters();
        }
        turnData = new byte[1];

        ArrayList<Participant> allPlayers = mCurrentMatch.getParticipants();
        int nextPlayerNumber = Integer.parseInt(mCurrentMatch.getLastUpdaterId().substring(2));
        try {
            //Should pass invitation to the next player
            String nextPlayerId = allPlayers.get(nextPlayerNumber).getParticipantId();
            Games.TurnBasedMultiplayer.takeTurn(mGoogleApiClient, mCurrentMatchId, turnData, nextPlayerId);

            //Grab the next player in case the previous above didn't work
            nextPlayerId = allPlayers.get(nextPlayerNumber + 1).getParticipantId();
            Games.TurnBasedMultiplayer.takeTurn(mGoogleApiClient, mCurrentMatchId, turnData, nextPlayerId);
        } catch (IndexOutOfBoundsException indexOutOfBonds) {
            Games.TurnBasedMultiplayer.takeTurn(mGoogleApiClient, mCurrentMatchId, turnData, mCurrentMatch.getPendingParticipantId());
        }

        registerMatchUpdateListener();
    }

    public void createCampaign(int campaignLength) {
        Calendar campaignCalendar = Calendar.getInstance();
        campaignCalendar.set(Calendar.HOUR, 18);
        campaignCalendar.add(Calendar.DATE, campaignLength);
        Intent intent = new Intent(this, CampaignEndAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, CampaignEndAlarmReceiver.REQUEST_CODE, intent, 0);
        AlarmManager am = (AlarmManager) getApplicationContext().getSystemService(ALARM_SERVICE);
        am.set(AlarmManager.RTC_WAKEUP, campaignCalendar.getTimeInMillis(), pendingIntent);
        Log.d("CreateCampaign", "Campaign Created");
    }

    public void saveSafehouse() {
        Firebase safehouseFirebaseRef = new Firebase(Constants.FIREBASE_URL_SAFEHOUSES + "/" + mNextSafeHouseId + "/");
        safehouseFirebaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String houseName = dataSnapshot.child("houseName").getValue().toString();
                String description = dataSnapshot.child("description").getValue().toString();
                int stepsRequired = Integer.parseInt(dataSnapshot.child("stepsRequired").getValue().toString());

                // Build the next safehouse object and save it to shared preferences
                SafeHouse nextSafeHouse = new SafeHouse(mNextSafeHouseId, houseName, description);
                Gson gson = new Gson();
                String nextSafehouseJson = gson.toJson(nextSafeHouse);
                mEditor.putString("nextSafehouse", nextSafehouseJson);
                mEditor.commit();
                String safehouseJson = mSharedPreferences.getString("nextSafehouse", null);
                Gson safehouseGson = new Gson();
                mNextSafehouse = safehouseGson.fromJson(safehouseJson, SafeHouse.class);
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {}
        });
    }

    private void assignRandomCharacters() {
        final Firebase characterSkeletonRef = new Firebase(Constants.FIREBASE_URL+ "/");
        final ArrayList<Character> selectionList = new ArrayList<>();

        characterSkeletonRef.child("characters").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot child : dataSnapshot.getChildren()) {
                    String name = child.child("name").getValue().toString();
                    long ageLong = (long) child.child("age").getValue();
                    int age = (int) ageLong;
                    String description = child.child("description").getValue().toString();
                    long characterIdLong = (long) child.child("characterId").getValue();
                    int characterId = (int) characterIdLong;
                    long healthLong = (long) child.child("health").getValue();
                    int health = (int) healthLong;
                    long fullnessLevelLong = (long) child.child("fullnessLevel").getValue();
                    int fullnessLevel = (int) fullnessLevelLong;
                    String characterUrl = child.child("characterPictureUrl").getValue().toString();
                    Character character = new Character(name, description, age, health, fullnessLevel, characterUrl, characterId);
                    selectionList.add(character);

                    Firebase characterFirebaseRef = new Firebase(Constants.FIREBASE_URL_TEAM + "/" + mCurrentMatchId + "/characters");
                    turnData = mCurrentMatch.getData();
                    Collections.shuffle(selectionList);
                    if (turnData == null && invitedPlayers != null) {
                        for (int i = 0; i < invitedPlayers.size(); i++) {
                            try {
                                Character assignedCharacter = selectionList.get(i);
                                String playerBeingAssignId = invitedPlayers.get(i);

                                //save assigned character Ids to firebase
                                characterFirebaseRef.child(playerBeingAssignId)
                                        .setValue((selectionList.get(i).getCharacterId()));

                                Firebase userRef = new Firebase(Constants.FIREBASE_URL_USERS + "/" + playerBeingAssignId + "/");
                                userRef.child("character").setValue(assignedCharacter);
                            } catch (IndexOutOfBoundsException indexOutOfBounds) {
                                indexOutOfBounds.getStackTrace();
                            }
                        }

                    }
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }
    private void assignStarterInventory() {
        for (int i = 0; i < invitedPlayers.size(); i++) {
            try {
                Collections.shuffle(allWeapons);
                Collections.shuffle(allMedicine);
                Collections.shuffle(allFood);
                ArrayList<Item> itemsToPush = new ArrayList<>();
                Weapon freebieWeapon = allWeapons.get(0);
                Item freebieFoodOne = allFood.get(0);
                itemsToPush.add(freebieFoodOne);
                Item freebieFoodTwo = allFood.get(1);
                itemsToPush.add(freebieFoodTwo);
                Item freebieMedicineOne = allMedicine.get(0);
                itemsToPush.add(freebieMedicineOne);
                Item freebieMedicineTwo = allMedicine.get(1);
                itemsToPush.add(freebieMedicineTwo);

                String playerBeingAssignId = invitedPlayers.get(i);

                for(int j = 0; j < itemsToPush.size(); j++) {
                    Item item = itemsToPush.get(j);
                    Firebase itemRef = new Firebase (Constants.FIREBASE_URL_USERS + "/" + playerBeingAssignId + "/items");
                    Firebase newItemRef = itemRef.push();
                    String itemPushId = newItemRef.getKey();
                    item.setPushId(itemPushId);
                    newItemRef.setValue(item);
                }


                Firebase weaponRef = new Firebase (Constants.FIREBASE_URL_USERS + "/" + playerBeingAssignId + "/weapons");
                Firebase newWeaponRef = weaponRef.push();
                String weaponPushId = newWeaponRef.getKey();

                freebieWeapon.setPushId(weaponPushId);
                newWeaponRef.setValue(freebieWeapon);

            } catch (IndexOutOfBoundsException indexOutOfBounds) {
                indexOutOfBounds.getStackTrace();
            }
        }

    }

    public void saveCampaignSettings() {
        mEditor.putInt(Constants.PREFERENCES_DURATION_SETTING, mCampaignLength);
        mEditor.putInt(Constants.PREFERENCES_DEFAULT_DAILY_GOAL_SETTING, mDifficultyLevel);
        mEditor.commit();
    }

    @Override
    public void onSignInFailed() {}

    @Override
    public void onSignInSucceeded() {}

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v("NewCampaign", intent.getAction().toString());
            if(intent.getAction().equals(RECEIVE_UPDATE_FROM_INVITATION)) {
                boolean matchMakingDone = intent.getBooleanExtra(Constants.INVITATION_UPDATE_INTENT_EXTRA, false);
                if (matchMakingDone) {
                    Intent updateIntent = new Intent(NewCampaignActivity.this, MainActivity.class);
                    startActivity(updateIntent);
                }

//            } else if (intent.getAction().equals(RECEIVE_UPDATE_FROM_MATCH)) {
//                boolean playerAcceptedInvite = intent.getBooleanExtra(Constants.MATCH_UPDATE_INTENT_EXTRA, false);
//                if (playerAcceptedInvite) {
//                    String playerWhoUpdated = intent.getStringExtra(Constants.MATCH_UPDATE_INTENT_EXTRA_PLAYER);
//
//                }
            }
        }
    };

    public void registerMatchUpdateListener() {
        Games.TurnBasedMultiplayer.registerMatchUpdateListener(mGoogleApiClient, new OnTurnBasedMatchUpdateReceivedListener() {
            @Override
            public void onTurnBasedMatchReceived(TurnBasedMatch turnBasedMatch) {
                int gameStatus = turnBasedMatch.getStatus();
                int gameStarted = TurnBasedMatch.MATCH_STATUS_ACTIVE;
                ArrayList<String> totalParty = turnBasedMatch.getParticipantIds();
                ArrayList<String> tallyOfPlayersJoined = new ArrayList<>();
                tallyOfPlayersJoined.add(turnBasedMatch.getCreatorId());
                boolean uiIsntYetUpdated = true;

                if (gameStatus == gameStarted) {
                    Toast.makeText(NewCampaignActivity.this, turnBasedMatch.getParticipant(turnBasedMatch.getLastUpdaterId()).getDisplayName() + " accepted invite", Toast.LENGTH_LONG).show();
                    tallyOfPlayersJoined.add(turnBasedMatch.getLastUpdaterId());

                    if (tallyOfPlayersJoined.size() == totalParty.size() && uiIsntYetUpdated) {
                        Toast.makeText(NewCampaignActivity.this, "Game has started", Toast.LENGTH_LONG).show();
                        Intent moveToMain = new Intent(NewCampaignActivity.this, MainActivity.class);
                        startActivity(moveToMain);
                        uiIsntYetUpdated = false;
                        Log.v("TAG", tallyOfPlayersJoined.size() + "");
                    }
                }
            }
            @Override
            public void onTurnBasedMatchRemoved(String s) {

            }
        });
    }

    public Context getContext() {
        return mContext;
    }
}
