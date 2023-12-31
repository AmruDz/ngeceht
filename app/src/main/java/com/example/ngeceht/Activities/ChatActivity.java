package com.example.ngeceht.Activities;

import androidx.annotation.NonNull;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;

import com.example.ngeceht.Adapters.ChatAdapter;
import com.example.ngeceht.Models.ChatMessage;
import com.example.ngeceht.Models.User;
import com.example.ngeceht.Network.ApiClient;
import com.example.ngeceht.Network.ApiService;
import com.example.ngeceht.Utilities.Constans;
import com.example.ngeceht.Utilities.PreferencesManager;
import com.example.ngeceht.databinding.ActivityChatBinding;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends BaseActivity {
    private ActivityChatBinding binding;
    private User receiverUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferencesManager preferencesManager;
    private FirebaseFirestore database;
    private String conversionId = null;
    private Boolean isReceiverAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
        loadReceiverDetails();
        init();
        listenMessages();
    }

    private void init() {
        preferencesManager = new PreferencesManager(getApplicationContext());
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(
                chatMessages,
                getBitmapFromEncodedString(receiverUser.image),
                preferencesManager.getString(Constans.KEY_USER_ID)
        );
        binding.chatRecyclerView.setAdapter(chatAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private void sendMessage() {
        HashMap<String, Object> message = new HashMap<>();
        message.put(Constans.KEY_SENDER_ID, preferencesManager.getString(Constans.KEY_USER_ID));
        message.put(Constans.KEY_RECEIVER_ID, receiverUser.id);
        message.put(Constans.KEY_MESSAGE, binding.inputMessage.getText().toString());
        message.put(Constans.KEY_TIMESTAMP, new Date());
        database.collection(Constans.KEY_COLLECTION_CHAT).add(message);
        if (conversionId != null) {
            updateConversion(binding.inputMessage.getText().toString());
        } else {
            HashMap<String, Object> conversion = new HashMap<>();
            conversion.put(Constans.KEY_SENDER_ID, preferencesManager.getString(Constans.KEY_USER_ID));
            conversion.put(Constans.KEY_SENDER_NAME, preferencesManager.getString(Constans.KEY_NAME));
            conversion.put(Constans.KEY_SENDER_IMAGE, preferencesManager.getString(Constans.KEY_IMAGE));
            conversion.put(Constans.KEY_RECEIVER_ID, receiverUser.id);
            conversion.put(Constans.KEY_RECEIVER_NAME, receiverUser.name);
            conversion.put(Constans.KEY_RECEIVER_IMAGE, receiverUser.image);
            conversion.put(Constans.KEY_LAST_MESSAGE, binding.inputMessage.getText().toString());
            conversion.put(Constans.KEY_TIMESTAMP, new Date());
            addConversion(conversion);
        }
        if (!isReceiverAvailable) {
            try {
                JSONArray tokens = new JSONArray();
                tokens.put(receiverUser.token);

                JSONObject data = new JSONObject();
                data.put(Constans.KEY_USER_ID, preferencesManager.getString(Constans.KEY_USER_ID));
                data.put(Constans.KEY_NAME, preferencesManager.getString(Constans.KEY_NAME));
                data.put(Constans.KEY_FCM_TOKEN, preferencesManager.getString(Constans.KEY_FCM_TOKEN));
                data.put(Constans.KEY_MESSAGE, binding.inputMessage.getText().toString());

                JSONObject body = new JSONObject();
                body.put(Constans.REMOTE_MSG_DATA, data);
                body.put(Constans.REMOTE_MSG_REGISTRATION_IDS, tokens);

                sendNotification(body.toString());
            } catch (Exception e) {
                showToast(e.getMessage());
            }
        }
        binding.inputMessage.setText(null);
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void sendNotification(String messageBody) {
        ApiClient.getClient().create(ApiService.class).sendMessage(
                Constans.getRemoteMsgHeaders(),
                messageBody
        ).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call,@NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    try {
                        if (response.body() != null) {
                            JSONObject responseJson = new JSONObject(response.body());
                            JSONArray results = responseJson.getJSONArray("result");
                            if (responseJson.getInt("failure") == 1) {
                                JSONObject error = (JSONObject) results.get(0);
                                showToast(error.getString("error"));
                                return;
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    showToast("Pesan berhasil dikirim");
                } else {
                    showToast("Error: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call,@NonNull Throwable t) {
                showToast(t.getMessage());
            }
        });
    }

    private void listenAvailabilityOfReceiver() {
        database.collection(Constans.KEY_COLLECTION_USERS).document(
                receiverUser.id
        ).addSnapshotListener(ChatActivity.this, (value, error) -> {
           if (error != null) {
               return;
           }
           if (value != null) {
               if (value.getLong(Constans.KEY_AVAILABILITY) != null) {
                   int availability = Objects.requireNonNull(
                        value.getLong(Constans.KEY_AVAILABILITY)
                   ).intValue();
                   isReceiverAvailable = availability == 1;
               }
               receiverUser.token = value.getString(Constans.KEY_FCM_TOKEN);
               if (receiverUser.image == null) {
                   receiverUser.image = value.getString(Constans.KEY_IMAGE);
                   chatAdapter.setReceiverProfileImage(getBitmapFromEncodedString(receiverUser.image));
                   chatAdapter.notifyItemRangeChanged(0, chatMessages.size());
               }
           }
           if (isReceiverAvailable) {
               binding.textAvailability.setVisibility(View.VISIBLE);
           } else {
               binding.textAvailability.setVisibility(View.GONE);
           }
        });
    }

    private void listenMessages() {
        database.collection(Constans.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constans.KEY_SENDER_ID, preferencesManager.getString(Constans.KEY_USER_ID))
                .whereEqualTo(Constans.KEY_RECEIVER_ID, receiverUser.id)
                .addSnapshotListener(eventListener);
        database.collection(Constans.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constans.KEY_SENDER_ID, receiverUser.id)
                .whereEqualTo(Constans.KEY_RECEIVER_ID, preferencesManager.getString(Constans.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if (error != null) {
            return;
        }
        if (value != null) {
            int count = chatMessages.size();
            for (DocumentChange documentChange : value.getDocumentChanges()) {
                if (documentChange.getType() == DocumentChange.Type.ADDED) {
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.senderId = documentChange.getDocument().getString(Constans.KEY_SENDER_ID);
                    chatMessage.receiverId = documentChange.getDocument().getString(Constans.KEY_RECEIVER_ID);
                    chatMessage.message = documentChange.getDocument().getString(Constans.KEY_MESSAGE);
                    chatMessage.dateTime = getReadableDateTime(documentChange.getDocument().getDate(Constans.KEY_TIMESTAMP));
                    chatMessage.dateObject = documentChange.getDocument().getDate(Constans.KEY_TIMESTAMP);
                    chatMessages.add(chatMessage);
                }
            }
            Collections.sort(chatMessages, (obj1, obj2) -> obj1.dateObject.compareTo(obj2.dateObject));
            if (count == 0) {
                chatAdapter.notifyDataSetChanged();
            } else {
                chatAdapter.notifyItemRangeInserted(chatMessages.size(), chatMessages.size());
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
            }
            binding.chatRecyclerView.setVisibility(View.VISIBLE);
        }
        binding.progressBar.setVisibility(View.GONE);
        if (conversionId == null) {
            checkForConversion();
        }
    };

    private Bitmap getBitmapFromEncodedString(String encodeImage) {
        if (encodeImage != null) {
            byte[] bytes = Base64.decode(encodeImage, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } else {
            return null;
        }
    }

    private void loadReceiverDetails() {
        receiverUser = (User) getIntent().getSerializableExtra(Constans.KEY_USER);
        binding.textName.setText(receiverUser.name);
    }

    private void setListeners() {
        binding.btnBack.setOnClickListener(v -> onBackPressed());
        binding.layoutSend.setOnClickListener(v -> sendMessage());
    }

    private String getReadableDateTime(Date date) {
        return new SimpleDateFormat("EEEE, dd MMMM yyyy - hh:mm a", Locale.getDefault()).format(date);
    }

    private void addConversion(HashMap<String, Object> conversion) {
        database.collection(Constans.KEY_COLLECTION_CONVERSATIONS)
                .add(conversion)
                .addOnSuccessListener(documentReference -> conversionId = documentReference.getId());
    }

    private void updateConversion(String message) {
        DocumentReference documentReference =
                database.collection(Constans.KEY_COLLECTION_CONVERSATIONS).document(conversionId);
        documentReference.update(
                Constans.KEY_LAST_MESSAGE, message,
                Constans.KEY_TIMESTAMP, new Date()
        );
    }

    private void checkForConversion() {
        if (chatMessages.size() != 0) {
            checkForConversionRemotely(
                    preferencesManager.getString(Constans.KEY_USER_ID),
                    receiverUser.id
            );
            checkForConversionRemotely(
                    receiverUser.id,
                    preferencesManager.getString(Constans.KEY_USER_ID)
            );
        }
    }

    private void checkForConversionRemotely(String senderId, String receiverId) {
        database.collection(Constans.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constans.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constans.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversionOnCompeleteListener);
    }

    private final OnCompleteListener<QuerySnapshot> conversionOnCompeleteListener = task -> {
        if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
            conversionId = documentSnapshot.getId();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReceiver();
    }
}