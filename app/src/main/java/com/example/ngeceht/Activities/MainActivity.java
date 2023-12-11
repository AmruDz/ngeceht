package com.example.ngeceht.Activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;

import com.example.ngeceht.Adapters.RecentChatsAdapter;
import com.example.ngeceht.Listeners.ConversionListener;
import com.example.ngeceht.Models.ChatMessage;
import com.example.ngeceht.Models.User;
import com.example.ngeceht.Utilities.Constans;
import com.example.ngeceht.Utilities.PreferencesManager;
import com.example.ngeceht.databinding.ActivityMainBinding;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends BaseActivity implements ConversionListener{
    private ActivityMainBinding binding;
    private PreferencesManager preferencesManager;
    private List<ChatMessage> conversations;
    private RecentChatsAdapter recentChatsAdapter;
    private FirebaseFirestore database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferencesManager = new PreferencesManager(getApplicationContext());
        init();
        loadUserDetails();
        getToken();
        setListeners();
        listenConversations();
    }

    private void init() {
        conversations = new ArrayList<>();
        recentChatsAdapter = new RecentChatsAdapter(conversations, this);
        binding.recentsRecyclerView.setAdapter(recentChatsAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private void setListeners() {
        binding.txtLogout.setOnClickListener(v -> Logout());
        binding.addNewChat.setOnClickListener(v ->
                startActivity(new Intent(getApplicationContext(), UserActivity.class)));
    }

    private void loadUserDetails() {
        binding.textName.setText(preferencesManager.getString(Constans.KEY_NAME));
        byte[] bytes = Base64.decode(preferencesManager.getString(Constans.KEY_IMAGE), Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        binding.imageProfile.setImageBitmap(bitmap);
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void listenConversations() {
        database.collection(Constans.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constans.KEY_SENDER_ID, preferencesManager.getString(Constans.KEY_USER_ID))
                .addSnapshotListener(eventListener);
        database.collection(Constans.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constans.KEY_RECEIVER_ID, preferencesManager.getString(Constans.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    private final EventListener<QuerySnapshot> eventListener = ((value, error) -> {
       if (error != null) {
           return;
       }
       if (value != null) {
           for (DocumentChange documentChange : value.getDocumentChanges()) {
               if (documentChange.getType() == DocumentChange.Type.ADDED) {
                   String senderId = documentChange.getDocument().getString(Constans.KEY_SENDER_ID);
                   String receiverId = documentChange.getDocument().getString(Constans.KEY_RECEIVER_ID);
                   ChatMessage chatMessage = new ChatMessage();
                   chatMessage.senderId = senderId;
                   chatMessage.receiverId = receiverId;
                   if (preferencesManager.getString(Constans.KEY_USER_ID).equals(senderId)){
                       chatMessage.conversionImage = documentChange.getDocument().getString(Constans.KEY_RECEIVER_IMAGE);
                       chatMessage.conversionName = documentChange.getDocument().getString(Constans.KEY_RECEIVER_NAME);
                       chatMessage.conversionId = documentChange.getDocument().getString(Constans.KEY_RECEIVER_ID);
                   } else {
                       chatMessage.conversionImage = documentChange.getDocument().getString(Constans.KEY_SENDER_IMAGE);
                       chatMessage.conversionName = documentChange.getDocument().getString(Constans.KEY_SENDER_NAME);
                       chatMessage.conversionId = documentChange.getDocument().getString(Constans.KEY_SENDER_ID);
                   }
                   chatMessage.message = documentChange.getDocument().getString(Constans.KEY_LAST_MESSAGE);
                   chatMessage.dateObject = documentChange.getDocument().getDate(Constans.KEY_TIMESTAMP);
                   conversations.add(chatMessage);
               } else if (documentChange.getType() == DocumentChange.Type.MODIFIED) {
                   for (int i = 0; i < conversations.size(); i++) {
                       String senderId = documentChange.getDocument().getString(Constans.KEY_SENDER_ID);
                       String receiverId = documentChange.getDocument().getString(Constans.KEY_RECEIVER_ID);
                       if (conversations.get(i).senderId.equals(senderId) && conversations.get(i).receiverId.equals(receiverId)) {
                            conversations.get(i).message = documentChange.getDocument().getString(Constans.KEY_LAST_MESSAGE);
                            conversations.get(i).dateObject = documentChange.getDocument().getDate(Constans.KEY_TIMESTAMP);
                            break;
                       }
                   }
               }
               Collections.sort(conversations, (obj1, obj2) -> obj2.dateObject.compareTo(obj1.dateObject));
               recentChatsAdapter.notifyDataSetChanged();
               binding.recentsRecyclerView.smoothScrollToPosition(0);
               binding.recentsRecyclerView.setVisibility(View.VISIBLE);
               binding.progressBar.setVisibility(View.GONE);
           }
       }
    });

    private void getToken() {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(this::updateToken);
    }

    private void updateToken(String token) {
        preferencesManager.putString(Constans.KEY_FCM_TOKEN, token);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference =
                database.collection(Constans.KEY_COLLECTION_USERS).document(
                        preferencesManager.getString(Constans.KEY_USER_ID)
                );
        documentReference.update(Constans.KEY_FCM_TOKEN, token)
                .addOnFailureListener(unused -> showToast("Tidak Dapat Mengupdate Token"));
    }

    private void Logout() {
        showToast("Logout...");
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference =
                database.collection(Constans.KEY_COLLECTION_USERS).document(
                  preferencesManager.getString(Constans.KEY_USER_ID)
                );
        HashMap<String, Object> updates = new HashMap<>();
        updates.put(Constans.KEY_FCM_TOKEN, FieldValue.delete());
        documentReference.update(updates)
                .addOnSuccessListener(unused -> {
                    preferencesManager.clear();
                    startActivity(new Intent(getApplicationContext(), SignInActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> showToast("Logout Gagal"));
    }

    @Override
    public void onConversionClicked(User user) {
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constans.KEY_USER, user);
        startActivity(intent);
    }
}