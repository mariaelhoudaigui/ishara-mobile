// Contenu pour QuizCategoryModel.java
package com.example.signlanguagetranslator;

public class QuizCategoryModel {
    private String categoryName;
    private String categoryId;

    // Constructeur vide nécessaire pour Firebase
    public QuizCategoryModel() {}

    public QuizCategoryModel(String categoryName, String categoryId) {
        this.categoryName = categoryName;
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }
}
