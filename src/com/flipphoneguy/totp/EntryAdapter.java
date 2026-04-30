package com.flipphoneguy.totp;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class EntryAdapter extends ArrayAdapter<TotpEntry> {

    public EntryAdapter(Context ctx, List<TotpEntry> entries) {
        super(ctx, 0, entries);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder h;
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                .inflate(R.layout.item_totp, parent, false);
            h = new ViewHolder();
            h.avatar  = convertView.findViewById(R.id.avatar);
            h.name    = convertView.findViewById(R.id.name);
            h.code    = convertView.findViewById(R.id.code);
            h.seconds = convertView.findViewById(R.id.seconds);
            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }

        TotpEntry e = getItem(position);
        if (e == null) return convertView;

        String displayName = e.name == null ? "?" : e.name;
        h.name.setText(displayName);
        h.avatar.setText(displayName.isEmpty() ? "?" : displayName.substring(0, 1).toUpperCase());

        // Format code as "123 456"
        String c = TotpGenerator.code(e.seed);
        if (c.length() == 6) c = c.substring(0, 3) + " " + c.substring(3);
        h.code.setText(c);

        int rem = TotpGenerator.secondsRemaining();
        h.seconds.setText(String.valueOf(rem));
        if (rem <= 5) {
            h.seconds.setTextColor(getContext().getResources().getColor(R.color.warn));
        } else {
            h.seconds.setTextColor(getContext().getResources().getColor(R.color.text_primary));
        }

        return convertView;
    }

    static class ViewHolder {
        TextView avatar;
        TextView name;
        TextView code;
        TextView seconds;
    }
}
