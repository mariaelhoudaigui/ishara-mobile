// Contenu pour QuizCategoryActivity.java (Version Corrigée)
package com.example.signlanguagetranslator;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class QuizCategoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private QuizCategoryAdapter adapter;
    private List<QuizCategoryModel> categoryList = new ArrayList<>();
    private FirebaseFirestore db;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_category); // <- use the correct layout

        recyclerView = findViewById(R.id.categories_recycler_view);
        progressBar = findViewById(R.id.progress_bar);

        db = FirebaseFirestore.getInstance();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new QuizCategoryAdapter(this, categoryList);
        recyclerView.setAdapter(adapter);

        fetchCategories();
    }
    private void fetchCategories() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE); // Cacher la liste pendant le chargement
        db.collection("quizzes")
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE); // Afficher la liste après le chargement
                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            Toast.makeText(this, "Aucune catégorie de quiz trouvée.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        categoryList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String categoryId = document.getId();
                            // On suppose que le nom est l'ID avec la première lettre en majuscule
                            String categoryName = categoryId.substring(0, 1).toUpperCase() + categoryId.substring(1);
                            categoryList.add(new QuizCategoryModel(categoryName, categoryId));
                        }
                        adapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(this, "Erreur de chargement des catégories.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // --- Adaptateur interne pour la simplicité (MAINTENANT CORRIGÉ) ---
    public static class QuizCategoryAdapter extends RecyclerView.Adapter<QuizCategoryAdapter.CategoryViewHolder> {
        private Context context;
        private List<QuizCategoryModel> categoryList;

        public QuizCategoryAdapter(Context context, List<QuizCategoryModel> categoryList) {
            this.context = context;
            this.categoryList = categoryList;
        }

        @NonNull
        @Override
        public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // CORRECTION : On utilise ("inflate") notre nouveau fichier item_category.xml
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
            return new CategoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
            QuizCategoryModel category = categoryList.get(position);
            holder.categoryName.setText(category.getCategoryName());

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, QuizActivity.class);
                intent.putExtra("CATEGORY_ID", category.getCategoryId());
                context.startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return categoryList.size();
        }

        public static class CategoryViewHolder extends RecyclerView.ViewHolder {
            TextView categoryName;
            public CategoryViewHolder(@NonNull View itemView) {
                super(itemView);
                // CORRECTION : On cherche le TextView par son ID dans le fichier item_category.xml
                categoryName = itemView.findViewById(R.id.category_name_textview);
            }
        }
    }
}
