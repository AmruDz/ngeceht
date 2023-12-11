package com.example.ngeceht.Activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.example.ngeceht.Adapters.UsersAdapter;
import com.example.ngeceht.Listeners.UserListener;
import com.example.ngeceht.Models.User;
import com.example.ngeceht.R;
import com.example.ngeceht.Utilities.Constans;
import com.example.ngeceht.Utilities.PreferencesManager;
import com.example.ngeceht.databinding.ActivityMainBinding;
import com.example.ngeceht.databinding.ActivityUserBinding;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class UserActivity extends BaseActivity implements UserListener {
    private ActivityUserBinding binding;
    private PreferencesManager preferencesManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferencesManager = new PreferencesManager(getApplicationContext());
        setListeners();
        getUsers();
    }

    private void setListeners() {
        binding.btnBack.setOnClickListener(v -> onBackPressed());
    }

    private void getUsers() {
        loading(true);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection(Constans.KEY_COLLECTION_USERS)
                .get()
                .addOnCompleteListener(task -> {
                   loading(false);
                   String currentUserId = preferencesManager.getString(Constans.KEY_USER_ID);
                   if (task.isSuccessful() && task.getResult() != null) {
                       List<User> users = new ArrayList<>();
                       for (QueryDocumentSnapshot queryDocumentSnapshot : task.getResult()) {
                           if (currentUserId.equals(queryDocumentSnapshot.getId())) {
                               continue;
                           }
                           User user = new User();
                           user.name = queryDocumentSnapshot.getString(Constans.KEY_NAME);
                           user.email = queryDocumentSnapshot.getString(Constans.KEY_EMAIL);
                           user.image = queryDocumentSnapshot.getString(Constans.KEY_IMAGE);
                           user.token = queryDocumentSnapshot.getString(Constans.KEY_FCM_TOKEN);
                           user.id = queryDocumentSnapshot.getId();
                           users.add(user);
                       }
                       if (users.size() > 0) {
                           UsersAdapter usersAdapter = new UsersAdapter(users, this);
                           binding.usersRecyclerView.setAdapter(usersAdapter);
                           binding.usersRecyclerView.setVisibility(View.VISIBLE);
                       } else {
                           showErrorMessage();
                       }
                   } else {
                       showErrorMessage();
                   }
                });
    }
    private void showErrorMessage() {
        binding.errorMessage.setText(String.format("%s", "Belum ada user tersedia"));
        binding.errorMessage.setVisibility(View.VISIBLE);
    }

    private void loading(Boolean isLoading) {
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onUserClicked(User user) {
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constans.KEY_USER, user);
        startActivity(intent);
        finish();
    }
}