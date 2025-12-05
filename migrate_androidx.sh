#!/bin/bash

echo "Iniciando migração para AndroidX..."

# Definindo o sistema operacional para ajustar o comando sed (MacOS vs Linux)
OS="$(uname)"

# Função para substituir texto
replace_text() {
    local search="$1"
    local replace="$2"
    
    echo "Substituindo: $search -> $replace"

    if [ "$OS" = "Darwin" ]; then
        # Sintaxe para MacOS
        grep -rl "$search" . --include="*.java" --include="*.xml" | xargs sed -i '' "s/$search/$replace/g"
    else
        # Sintaxe para Linux (GitHub Actions / Ubuntu)
        grep -rl "$search" . --include="*.java" --include="*.xml" | xargs sed -i "s/$search/$replace/g"
    fi
}

# --- SUBSTITUIÇÕES ESPECÍFICAS SOLICITADAS ---

# 1. AppCompatActivity
replace_text "android.support.v7.app.AppCompatActivity" "androidx.appcompat.app.AppCompatActivity"

# 2. NonNull
replace_text "android.support.annotation.NonNull" "androidx.annotation.NonNull"

# 3. Nullable (Geralmente vem junto com o NonNull)
replace_text "android.support.annotation.Nullable" "androidx.annotation.Nullable"

# --- MUDANÇAS NO GRADLE (Essenciais para AndroidX funcionar) ---

PROPERTIES_FILE="gradle.properties"

if [ ! -f "$PROPERTIES_FILE" ]; then
    echo "Criando gradle.properties..."
    touch "$PROPERTIES_FILE"
fi

# Verifica e adiciona as flags necessárias no gradle.properties
if ! grep -q "android.useAndroidX=true" "$PROPERTIES_FILE"; then
    echo "Adicionando android.useAndroidX=true no gradle.properties"
    echo "android.useAndroidX=true" >> "$PROPERTIES_FILE"
fi

if ! grep -q "android.enableJetifier=true" "$PROPERTIES_FILE"; then
    echo "Adicionando android.enableJetifier=true no gradle.properties"
    echo "android.enableJetifier=true" >> "$PROPERTIES_FILE"
fi

echo "Migração concluída!"
