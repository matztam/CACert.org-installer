# Automatic Root for the Android Emulator

echo 'Mounting /system...'
sleep 0.5
adb shell mount -o rw,remount -t yaffs2 /dev/block/mtdblock0 /system
echo 'Pushing su binary...'
sleep 0.5
adb push su /system/xbin/su
echo 'Pushing busybox binary...'
sleep 0.5
adb push busybox /system/xbin/busybox
echo 'Installing Superuser...'
sleep 0.5
adb install Superuser.apk
echo 'Setting permissions...'
sleep 0.5
adb shell chmod 06755 /system/xbin/su
adb shell chmod 06755 /system/xbin/busybox
