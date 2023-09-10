package rl.track;

import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private File orderFile;
    private BaseAdapter ap;
    private final MediaPlayer mediaPlayer = new MediaPlayer();
    private final ArrayList<String> orderList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // view setting
        {
            findViewById(R.id.version).setOnClickListener(view -> {
                new AlertDialog.Builder(this)
                        .setTitle("Actions")
                        .setPositiveButton("Delete order.txt", (dialogInterface, i) -> {
                            orderFile.delete();
                            finish();
                        })
                        .setNeutralButton("Clear Files", (dialogInterface, i) -> {
                            new File(getFilesDir().toURI()).delete();
                            finish();
                        })
                        .show();
            });
            findViewById(R.id.stop).setOnClickListener(view -> mediaPlayer.stop());

            ListView listView = findViewById(R.id.listview);
            listView.setOnItemClickListener((adapterView, view, i, l) -> {
                try {
                    play(Uri.fromFile(new File(getFilesDir(), orderList.get(i))));
                } catch (Exception e) {
                    catchException(e);
                }
            });
            listView.setOnItemLongClickListener((adapterView, view, i, l) -> {
                new AlertDialog.Builder(this)
                        .setTitle(orderList.get(i))
                        .setPositiveButton("Delete", (dialogInterface, i1) -> {
                            new File(getFilesDir(), orderList.get(i)).delete();
                            orderList.remove(i);
                            ap.notifyDataSetChanged();

                            try {
                                FileWriter fw = new FileWriter(orderFile);
                                for (String audio : orderList) {
                                    fw.write(audio + '\n');
                                }
                                fw.close();
                            } catch (IOException e) {
                                catchException(e);
                            }
                        })
                        .show();
                return true;
            });
            class MyAdapter extends BaseAdapter {
                @Override
                public int getCount() {
                    return orderList.size();
                }

                @Override
                public String getItem(int i) {
                    return orderList.get(i);
                }

                @Override
                public long getItemId(int i) {
                    return i;
                }

                @Override
                public View getView(int i, View view, ViewGroup viewGroup) {
                    if (view == null) {
                        view = getLayoutInflater().inflate(R.layout.list_item, null);
                    }
                    ((TextView) view.findViewById(R.id.textview)).setText(getItem(i));

                    return view;
                }
            }
            ap = new MyAdapter();
            listView.setAdapter(ap);
        }


        // orderFile & orderList
        {
            orderFile = new File(getFilesDir(), "order.txt");
            if (!orderFile.exists()) {
                try {
                    orderFile.createNewFile();
                    FileWriter fw = new FileWriter(orderFile, true);

                    for (File file : new File(getFilesDir().toURI()).listFiles()) {
                        if ("order.txt".equals(file.getName())) continue;
                        fw.write(file.getName() + '\n');
                    }
                    fw.close();
                } catch (IOException e) {
                    catchException(e);
                }
            }

            try {
                BufferedReader br = new BufferedReader(new FileReader(orderFile));
                String line;
                orderList.clear();
                while ((line = br.readLine()) != null) {
                    orderList.add(line);
                }
                br.close();
            } catch (Exception e) {
                catchException(e);
            }

            ap.notifyDataSetChanged();
        }


        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        ArrayList<Uri> uriArrayList;

        if (action.equals(Intent.ACTION_SEND) && type != null) {
            // single
            uriArrayList = new ArrayList<>();
            uriArrayList.add(getIntent().getParcelableExtra(Intent.EXTRA_STREAM));
        } else if (action.equals(Intent.ACTION_SEND_MULTIPLE) && type != null) {
            // multi
            uriArrayList = getIntent().getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        } else {
            // not sharing
            return;
        }

        View layout = View.inflate(this, R.layout.generate_dialog, null);
        EditText editText = layout.findViewById(R.id.edittext);

        String[] stringArray = getMultiFileNameAndSize(uriArrayList);
        editText.setText(stringArray[0]);
        long totalSizeBytes = Long.parseLong(stringArray[1]);
        Toast.makeText(this, totalSizeBytes / 1000 + "KB", Toast.LENGTH_LONG).show();

        AlertDialog.Builder adb = new AlertDialog.Builder(this)
                .setView(layout)
                .setTitle("Name it !")
                .setCancelable(false)
                .setNegativeButton("Discard", (dialogInterface, i) -> finish())
                .setPositiveButton("Generate", null)
                .setNeutralButton("Overwrite", (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    try {
                        createAudioFiles(uriArrayList, editText.getText().toString());
                    } catch (Exception e) {
                        catchException(e);
                    }
                    finish();
                });
        AlertDialog ad = adb.show();
        ad.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String[] tempArray = editText.getText().toString().split("\n");
            for (String temp : tempArray) {
                if (orderList.contains(temp)) {
                    Toast.makeText(this, "The File Already Exists", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            ad.dismiss();
            try {
                createAudioFiles(uriArrayList, editText.getText().toString());
            } catch (Exception e) {
                catchException(e);
            }

            finish();
        });
    }

    @Override
    protected void onDestroy() {
        mediaPlayer.stop();
        super.onDestroy();
    }

    private String[] getMultiFileNameAndSize(ArrayList<Uri> uris) {
        StringBuilder sb = new StringBuilder();
        long totalSizeBytes = 0;

        for (Uri uri : uris) {
            Cursor returnCursor = getContentResolver().query(uri, null, null, null, null);
            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
            returnCursor.moveToFirst();

            String name = returnCursor.getString(nameIndex);
            long sizeBytes = returnCursor.getLong(sizeIndex);

            sb.append(name).append('\n');
            totalSizeBytes += sizeBytes;
        }
        return new String[]{sb.toString(), String.valueOf(totalSizeBytes)};
    }

    private void createAudioFiles(ArrayList<Uri> uris, String multiFileName) throws Exception {
        String[] fileNameArray = multiFileName.split("\n");

        // update multiFileName
        BufferedWriter bw = new BufferedWriter(new FileWriter(orderFile, true));
        bw.write(multiFileName);
        bw.newLine();
        bw.close();

        for (int i = 0; i < uris.size(); i++) {
            Uri uri = uris.get(i);
            String fileName = fileNameArray[i];

            // create audio file
            BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(new File(getFilesDir(), fileName).toPath()));
            BufferedInputStream bis = new BufferedInputStream(getContentResolver().openInputStream(uri));
            byte[] buffer = new byte[1024];
            while (bis.read(buffer) != -1) {
                bos.write(buffer);
            }
            bos.flush();
            bis.close();
            bos.close();

            // update dataList
            if (!orderList.contains(fileName)) {
                orderList.add(multiFileName);
                ap.notifyDataSetChanged();
            }
        }
    }

    private void play(Uri uri) throws Exception {
        mediaPlayer.reset();
        mediaPlayer.setDataSource(this, uri);
        mediaPlayer.prepare();
        mediaPlayer.start();
    }

    private void catchException(Exception e) {
        Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
    }
}