package io.github.elyx0.reactnativedocumentpicker;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @see <a href="https://developer.android.com/guide/topics/providers/document-provider.html">android documentation</a>
 */
public class DocumentPickerModule extends ReactContextBaseJavaModule {
	private static final String NAME = "RNDocumentPicker";
  private static final int READ_REQUEST_CODE = 41;

  private static final String E_ACTIVITY_DOES_NOT_EXIST = "ACTIVITY_DOES_NOT_EXIST";
  private static final String E_FAILED_TO_SHOW_PICKER = "FAILED_TO_SHOW_PICKER";
  private static final String E_DOCUMENT_PICKER_CANCELED = "DOCUMENT_PICKER_CANCELED";
  private static final String E_UNABLE_TO_OPEN_FILE_TYPE = "UNABLE_TO_OPEN_FILE_TYPE";
  private static final String E_UNKNOWN_ACTIVITY_RESULT = "UNKNOWN_ACTIVITY_RESULT";
  private static final String E_INVALID_DATA_RETURNED = "INVALID_DATA_RETURNED";
  private static final String E_UNEXPECTED_EXCEPTION = "UNEXPECTED_EXCEPTION";

  private static final String OPTION_TYPE = "type";
  private static final String OPTION_MULIPLE = "multiple";

  private static final String FIELD_URI = "uri";
  private static final String FIELD_FILE_COPY_URI = "fileCopyUri";
  private static final String FIELD_NAME = "name";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_SIZE = "size";

  private final ActivityEventListener activityEventListener = new BaseActivityEventListener() {
    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
      if (requestCode == READ_REQUEST_CODE) {
        if (promise != null) {
          onShowActivityResult(resultCode, data, promise);
          promise = null;
        }
      }
      }
  };

  private Promise promise;

  public DocumentPickerModule(ReactApplicationContext reactContext) {
    super(reactContext);
    reactContext.addActivityEventListener(activityEventListener);
  }

  @Override
  public void onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy();
    getReactApplicationContext().removeActivityEventListener(activityEventListener);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @ReactMethod
  public void pick(ReadableMap args, Promise promise) {
    Activity currentActivity = getCurrentActivity();

    if (currentActivity == null) {
      promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Current activity does not exist");
      return;
    }

    this.promise = promise;

    try {
      Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);

      intent.setType("*/*");
      if (!args.isNull(OPTION_TYPE)) {
        ReadableArray types = args.getArray(OPTION_TYPE);
        if (types != null && types.size() > 1) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
              intent.putExtra(Intent.EXTRA_MIME_TYPES, Arguments.toList(types));
            } else {
              Log.e(NAME, "Multiple type values not supported below API level 19");
            }
        } else if (types.size() == 1) {
          intent.setType(types.getString(0));
        }
      }

      boolean multiple = !args.isNull(OPTION_MULIPLE) && args.getBoolean(OPTION_MULIPLE);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
      }

      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
        intent = Intent.createChooser(intent, null);
      }

      currentActivity.startActivityForResult(intent, READ_REQUEST_CODE, Bundle.EMPTY);
    } catch (ActivityNotFoundException e) {
      this.promise.reject(E_UNABLE_TO_OPEN_FILE_TYPE, e.getLocalizedMessage());
      this.promise = null;
    } catch (Exception e) {
      e.printStackTrace();
      this.promise.reject(E_FAILED_TO_SHOW_PICKER, e.getLocalizedMessage());
      this.promise = null;
    }
  }

  public void onShowActivityResult(int resultCode, Intent data, Promise promise) {
    if (resultCode == Activity.RESULT_CANCELED) {
      promise.reject(E_DOCUMENT_PICKER_CANCELED, "User canceled document picker");
    } else if (resultCode == Activity.RESULT_OK) {
      Uri uri = null;
      ClipData clipData = null;

      if (data != null) {
        uri = data.getData();
        clipData = data.getClipData();
      }

      try {
        WritableArray results = new WritableNativeArray();

        if (uri != null) {
          results.pushMap(getMetadata(uri));
        } else if (clipData != null && clipData.getItemCount() > 0) {
          final int length = clipData.getItemCount();
          for (int i = 0; i < length; ++i) {
            ClipData.Item item = clipData.getItemAt(i);
            results.pushMap(getMetadata(item.getUri()));
          }
        } else {
          promise.reject(E_INVALID_DATA_RETURNED, "Invalid data returned by intent");
          return;
        }

        promise.resolve(results);
        Log.e("DONE", "123123");
        } catch (Exception e) {
          promise.reject(E_UNEXPECTED_EXCEPTION, e.getLocalizedMessage(), e);
        }
    } else {
      promise.reject(E_UNKNOWN_ACTIVITY_RESULT, "Unknown activity result: " + resultCode);
    }
  }


    public static String getFilePathForN(Uri uri, Context context) throws IOException {
      Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
      /*
        * Get the column indexes of the data in the Cursor,
        *     * move to the first row in the Cursor, get the data,
        *     * and display it.
        * */
      int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
      int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
      returnCursor.moveToFirst();
      String name = (returnCursor.getString(nameIndex));
      File cachedFolder = new File(context.getFilesDir(), "cached");
      if (!cachedFolder.exists()) {
        boolean success = cachedFolder.mkdirs();
        if (!success) {
          throw new IOException("Create folder failed");
        }
      }
      File file = new File(cachedFolder, name);
      try {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        FileOutputStream outputStream = new FileOutputStream(file);
        int read = 0;
        int maxBufferSize = 1024 * 1024;
        int bytesAvailable = inputStream.available();

        //int bufferSize = 1024;
        int bufferSize = Math.min(bytesAvailable, maxBufferSize);

        final byte[] buffers = new byte[bufferSize];
        while ((read = inputStream.read(buffers)) != -1) {
          outputStream.write(buffers, 0, read);
        }
        inputStream.close();
        outputStream.close();
      } catch (Exception e) {
        Log.e("Exception", e.getMessage());
        throw e;
      } finally {
        returnCursor.close();
      }
      return file.getPath();
    }

    private WritableMap getMetadata(Uri fileUri) {
      WritableMap map = Arguments.createMap();
      try {
        String path = getFilePathForN(fileUri, getReactApplicationContext());
        File f = new File(path);
        Uri uri = Uri.fromFile(f);

        map.putString(FIELD_URI, uri.toString());
        int fileSize = Integer.parseInt(String.valueOf(f.length()));

        map.putInt(FIELD_SIZE, fileSize);
        String fileURL = PathUtils.getPath(this.getReactApplicationContext(), uri);
//        fileURL = fileURL == null ? "" : fileURL;
        map.putString("fileURL", fileURL);
        // TODO vonovak - FIELD_FILE_COPY_URI is implemented on iOS only (copyTo) settings
        map.putString(FIELD_FILE_COPY_URI, uri.toString());

        ContentResolver contentResolver = getReactApplicationContext().getContentResolver();

        String type = contentResolver.getType(uri);
        map.putString(FIELD_TYPE, type == null ? "" : type);

        try (Cursor cursor = contentResolver.query(uri, null, null, null, null, null)) {
          if (cursor != null && cursor.moveToFirst()) {
            int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (!cursor.isNull(displayNameIndex)) {
              map.putString(FIELD_NAME, cursor.getString(displayNameIndex));
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
              int mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
              if (!cursor.isNull(mimeIndex)) {
                map.putString(FIELD_TYPE, cursor.getString(mimeIndex));
              }
            }

            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            if (!cursor.isNull(sizeIndex)) {
              map.putInt(FIELD_SIZE, cursor.getInt(sizeIndex));
            }
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }


      return map;
    }

    public static class PathUtils {
        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is DownloadsProvider.
         */
        public static boolean isDownloadsDocument(Uri uri) {
            return "com.android.providers.downloads.documents".equals(uri
                    .getAuthority());
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is ExternalStorageProvider.
         */
        public static boolean isExternalStorageDocument(Uri uri) {
            return "com.android.externalstorage.documents".equals(uri
                    .getAuthority());
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is MediaProvider.
         */
        public static boolean isMediaDocument(Uri uri) {
            return "com.android.providers.media.documents".equals(uri
                    .getAuthority());
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is Google Photos.
         */
        public static boolean isGooglePhotosUri(Uri uri) {
            return "com.google.android.apps.photos.content".equals(uri
                    .getAuthority());
        }

        /**
         * Get the value of the data column for this Uri. This is useful for
         * MediaStore Uris, and other file-based ContentProviders.
         *
         * @param context       The context.
         * @param uri           The Uri to query.
         * @param selection     (Optional) Filter used in the query.
         * @param selectionArgs (Optional) Selection arguments used in the query.
         * @return The value of the _data column, which is typically a file path.
         */
        public static String getDataColumn(Context context, Uri uri,
                                           String selection, String[] selectionArgs) {

            Cursor cursor = null;
            final String column = "_data";
            final String[] projection = {column};

            try {
                cursor = context.getContentResolver().query(uri, projection,
                        selection, selectionArgs, null);
                if (cursor != null && cursor.moveToFirst()) {
                    final int index = cursor.getColumnIndexOrThrow(column);
                    return cursor.getString(index);
                }
            } finally {
                if (cursor != null)
                    cursor.close();
            }
            return null;
        }

        public static @Nullable
        String getFileName(Context context, Uri uri) {
            ContentResolver contentResolver = context.getContentResolver();


            try (Cursor cursor = contentResolver.query(uri, null, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (!cursor.isNull(displayNameIndex)) {
                        return cursor.getString(displayNameIndex);
                    }
                }
            }
            return null;
        }


        private static String getFilePathForN(Uri uri, Context context) throws IOException {
            Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
            /*
             * Get the column indexes of the data in the Cursor,
             *     * move to the first row in the Cursor, get the data,
             *     * and display it.
             * */
            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
            returnCursor.moveToFirst();
            String name = (returnCursor.getString(nameIndex));
            String size = (Long.toString(returnCursor.getLong(sizeIndex)));
            File cachedFolder = new File(context.getFilesDir(), "cached");
            if (!cachedFolder.exists()) {
                boolean success = cachedFolder.mkdirs();
                if (!success) {
                    throw new IOException("Create folder failed");
                }
            }
            File file = new File(cachedFolder, name);
            try {
                InputStream inputStream = context.getContentResolver().openInputStream(uri);
                FileOutputStream outputStream = new FileOutputStream(file);
                int read = 0;
                int maxBufferSize = 1024 * 1024;
                int bytesAvailable = inputStream.available();

                //int bufferSize = 1024;
                int bufferSize = Math.min(bytesAvailable, maxBufferSize);

                final byte[] buffers = new byte[bufferSize];
                while ((read = inputStream.read(buffers)) != -1) {
                    outputStream.write(buffers, 0, read);
                }
                inputStream.close();
                outputStream.close();
            } catch (Exception e) {
                Log.e("Exception", e.getMessage());
                throw e;
            } finally {
                returnCursor.close();
            }
            return file.getPath();
        }


        @TargetApi(Build.VERSION_CODES.KITKAT)
        public static String getPath(final Context context, final Uri uri) {
            // DocumentProvider
            if (DocumentsContract.isDocumentUri(context, uri)) {
                // ExternalStorageProvider
                if (isExternalStorageDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
				/*if ("primary".equalsIgnoreCase(type)) {
					return Environment.getExternalStorageDirectory() + "/"
							+ split[1];
				}*/
                    return Environment.getExternalStorageDirectory() + "/"
                            + split[1];
                    // TODO handle non-primary volumes
                }
                // DownloadsProvider
                else if (isDownloadsDocument(uri)) {
                    try {
                        return getFilePathForN(uri, context);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                // MediaProvider
                else if (isMediaDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
                    Uri contentUri = null;
                    if ("image".equals(type)) {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    } else if ("video".equals(type)) {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    } else if ("audio".equals(type)) {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    } else if ("content".equalsIgnoreCase(uri.getScheme())) {
                        if (isGooglePhotosUri(uri))
                            return uri.getLastPathSegment();
                        try {
                            return getFilePathForN(uri, context);
                        } catch (Exception e) {
                            return getDataColumn(context, uri, null, null);
                        }
                    }
                    final String selection = "_id=?";
                    final String[] selectionArgs = new String[]{split[1]};
                    return getDataColumn(context, contentUri, selection,
                            selectionArgs);
                }
            }
            // MediaStore (and general)
            else if ("content".equalsIgnoreCase(uri.getScheme())) {
                // Return the remote address
                if (isGooglePhotosUri(uri))
                    return uri.getLastPathSegment();
                try {
                    return getFilePathForN(uri, context);
                } catch (Exception e) {
                    return getDataColumn(context, uri, null, null);
                }
            }
            // File
            else if ("file".equalsIgnoreCase(uri.getScheme())) {
                return uri.getPath();
            }
            return null;
        }
    }

}
