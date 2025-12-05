#!/bin/bash

echo "=== Iniciando Correção dos Manifestos ==="

# 1. Corrigir os módulos (Remover atributo package redundante)
# Isso remove os avisos "Setting the namespace via the package attribute... is no longer supported"
echo "Limpando manifestos dos módulos..."

MODULES=("input" "multiplayer" "preferences" "roms" "snes9x" "abstractemulator")

for mod in "${MODULES[@]}"; do
    MANIFEST_PATH="$mod/src/main/AndroidManifest.xml"
    if [ -f "$MANIFEST_PATH" ]; then
        # Remove package="qualquer.coisa" mantendo o resto
        sed -i 's/package="[^"]*"//' "$MANIFEST_PATH"
        echo " - Corrigido: $MANIFEST_PATH"
    fi
done

# 2. Corrigir o manifesto principal do App (Adicionar android:exported)
# Como XML é complexo para editar com regex, vamos sobrescrever com a versão correta
echo "Corrigindo app/src/main/AndroidManifest.xml..."

cat <<EOF > app/src/main/AndroidManifest.xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    android:installLocation="preferExternal">

    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />

    <application
        android:name=".KSNESApplication"
        android:allowBackup="true"
        android:fullBackupContent="@xml/backup_descriptor"
        android:icon="@drawable/app_icon"
        android:label="@string/app_label">

        <activity
            android:name=".activity.MainActivity"
            android:configChanges="orientation|keyboardHidden"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity-alias
            android:name="CreateShortcuts"
            android:label="@string/launcher_shortcut_name"
            android:exported="true"
            android:targetActivity=".activity.MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.CREATE_SHORTCUT" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity-alias>

        <activity android:name=".activity.FileChooser" />
        <activity
            android:name=".activity.StateSlotsActivity"
            android:theme="@android:style/Theme.Dialog" />
        <activity android:name=".activity.KeyProfilesActivity" />
        <activity android:name=".activity.HelpActivity" />
        <activity
            android:name=".activity.DeviceListActivity"
            android:configChanges="orientation|keyboardHidden"
            android:theme="@android:style/Theme.Dialog" />

        <activity
            android:name=".activity.CheatsActivity"
            android:process=":emulator" />
        <activity
            android:name=".activity.EmulatorSettings"
            android:process=":emulator" />

        <activity
            android:name=".activity.EmulatorActivity"
            android:configChanges="orientation|keyboardHidden"
            android:launchMode="singleTask"
            android:process=":emulator"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:scheme="file" />
                <data android:mimeType="application/zip" />
                <data android:mimeType="application/octet-stream" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.EmulatorService"
            android:process=":emulator" />

    </application>
</manifest>
EOF

echo "=== Correção Concluída ==="
