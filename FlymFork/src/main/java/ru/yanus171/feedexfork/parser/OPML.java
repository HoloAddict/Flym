/**
 * Flym
 * <p/>
 * Copyright (c) 2012-2015 Frederic Julian
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * <p/>
 * <p/>
 * Some parts of this software are based on "Sparse rss" under the MIT license (see
 * below). Please refers to the original project to identify which parts are under the
 * MIT license.
 * <p/>
 * Copyright (c) 2010-2012 Stefan Handschuh
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ru.yanus171.feedexfork.parser;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

import ru.yanus171.feedexfork.Constants;
import ru.yanus171.feedexfork.MainApplication;
import ru.yanus171.feedexfork.R;
import ru.yanus171.feedexfork.provider.FeedData.FeedColumns;
import ru.yanus171.feedexfork.provider.FeedData.EntryColumns;
import ru.yanus171.feedexfork.provider.FeedData.FilterColumns;
import ru.yanus171.feedexfork.service.FetcherService;
import ru.yanus171.feedexfork.utils.FileUtils;
import ru.yanus171.feedexfork.utils.HtmlUtils;

import static ru.yanus171.feedexfork.Constants.FALSE;
import static ru.yanus171.feedexfork.Constants.TRUE;
import static ru.yanus171.feedexfork.provider.FeedData.EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI;

public class OPML {

    public static String GetAutoBackupOPMLFileName() { return  FileUtils.GetFolder() + "/HandyNewsReader_auto_backup.opml"; }

    private static final String START = "<?xml version='1.0' encoding='utf-8'?>\n<opml version='1.1'>\n<head>\n<title>Handy News Reader export</title>\n<dateCreated>";
    private static final String AFTER_DATE = "</dateCreated>\n</head>\n<body>\n";
    private static final String OUTLINE_TITLE = "\t<outline title='";
    private static final String OUTLINE_XMLURL = "' type='rss' xmlUrl='";
    private static final String OUTLINE_RETRIEVE_FULLTEXT = "' retrieveFullText='";
    private static final String OUTLINE_INLINE_CLOSING = "'/>\n";
    private static final String OUTLINE_NORMAL_CLOSING = "'>\n";
    private static final String OUTLINE_END = "\t</outline>\n";

    private static final String FILTER_TEXT = "\t\t<filter text='";
    private static final String FILTER_IS_REGEX = "' isRegex='";
    private static final String FILTER_IS_APPLIED_TO_TITLE = "' isAppliedToTitle='";
    private static final String FILTER_IS_ACCEPT_RULE = "' isAcceptRule='";
    private static final String FILTER_IS_MARK_AS_STARRED = "' isMarkAsStarred='";
    private static final String FILTER_CLOSING = "'/>\n";

    private static final String TAG_ENTRY = "entry";

    private static final String TAG_START = "\t\t<%s %s='";
    private static final String ATTR_VALUE = "' %s='";
    private static final String TAG_CLOSING = "'/>\n";

    private static final String TAG_PREF = "pref";
    private static final String ATTR_PREF_CLASSNAME = "classname";
    private static final String ATTR_PREF_VALUE = "value";
    private static final String ATTR_PREF_KEY = "key";

    private static final String CLOSING = "</body>\n</opml>\n";

    //private static final OPMLParser mParser = new OPMLParser();
    private static boolean mAutoBackupEnabled = true;

    public static
    void importFromFile(String filename) throws IOException, SAXException {
        if (GetAutoBackupOPMLFileName().equals(filename))
            mAutoBackupEnabled = false;  // Do not write the auto backup file while reading it...
        final int status = FetcherService.Status().Start( MainApplication.getContext().getString(R.string.importingFromFile) );
        try {
            final OPMLParser parser = new OPMLParser();
            Xml.parse(new InputStreamReader(new FileInputStream(filename)), parser);
            parser.mEditor.commit();
        } finally {
            mAutoBackupEnabled = true;
            FetcherService.Status().End( status );
        }
    }

    public static void importFromFile(InputStream input) throws IOException, SAXException {
        final OPMLParser parser = new OPMLParser();
        Xml.parse(new InputStreamReader(input), parser);
        parser.mEditor.commit();
    }


    public static void exportToFile(String filename) throws IOException {
        if (GetAutoBackupOPMLFileName().equals(filename) && !mAutoBackupEnabled)
            return;

        final int status = FetcherService.Status().Start( "Exporting to file ..." );

        Cursor cursorGroupsAndRoot = MainApplication.getContext().getContentResolver()
                .query(FeedColumns.GROUPS_AND_ROOT_CONTENT_URI, FEEDS_PROJECTION, null, null, null);

        StringBuilder builder = new StringBuilder(START);
        builder.append(System.currentTimeMillis());
        builder.append(AFTER_DATE);

        while (cursorGroupsAndRoot.moveToNext()) {
            if (cursorGroupsAndRoot.getInt(1) == 1) { // If it is a group
                builder.append(OUTLINE_TITLE);
                builder.append(cursorGroupsAndRoot.isNull(2) ? "" : TextUtils.htmlEncode(cursorGroupsAndRoot.getString(2)));
                builder.append(OUTLINE_NORMAL_CLOSING);
                Cursor cursorFeeds = MainApplication.getContext().getContentResolver()
                        .query(FeedColumns.FEEDS_FOR_GROUPS_CONTENT_URI(cursorGroupsAndRoot.getString(0)), FEEDS_PROJECTION, null, null, null);
                while (cursorFeeds.moveToNext()) {
                    ExportFeed(builder, cursorFeeds);
                }
                cursorFeeds.close();

                builder.append(OUTLINE_END);
            } else {
                ExportFeed(builder, cursorGroupsAndRoot);
            }
        }
        SaveSettings( builder, "\t\t" );
        builder.append(CLOSING);

        cursorGroupsAndRoot.close();

        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));

        writer.write(builder.toString());
        writer.close();
        FetcherService.Status().End( status );
    }

    private static String GetEncoded( final Cursor cursor, final int col ) {
        return cursor.isNull( col ) ? "" : TextUtils.htmlEncode(cursor.getString(col ) );
    }
    private static String GetText( Attributes attr, String attrName  ) {
        return attr.getValue( attrName );
    }

    private static final String[] FEEDS_PROJECTION = new String[]{FeedColumns._ID, FeedColumns.IS_GROUP, FeedColumns.NAME, FeedColumns.URL,
            FeedColumns.RETRIEVE_FULLTEXT, FeedColumns.SHOW_TEXT_IN_ENTRY_LIST, FeedColumns.IS_AUTO_REFRESH, FeedColumns.IS_IMAGE_AUTO_LOAD, FeedColumns.OPTIONS };

    private static void ExportFeed(StringBuilder builder, Cursor cursor) {
        final String feedID = cursor.getString(0);
        builder.append("\t");
        builder.append(OUTLINE_TITLE);
        builder.append(GetEncoded( cursor ,2) );
        builder.append(OUTLINE_XMLURL);
        builder.append(GetEncoded( cursor, 3));
        builder.append(OUTLINE_RETRIEVE_FULLTEXT);
        builder.append(GetBoolText( cursor, 4 ));
        builder.append(String.format( ATTR_VALUE, FeedColumns.SHOW_TEXT_IN_ENTRY_LIST ));
        builder.append(GetBoolText( cursor, 5 ));
        builder.append(String.format( ATTR_VALUE, FeedColumns.IS_AUTO_REFRESH ));
        builder.append(GetBoolText( cursor, 6 ));
        builder.append(String.format( ATTR_VALUE, FeedColumns.IS_IMAGE_AUTO_LOAD ));
        builder.append(GetBoolText( cursor, 7 ));
        builder.append(String.format( ATTR_VALUE, FeedColumns.OPTIONS ));
        builder.append(GetEncoded( cursor, 8 ));

        builder.append(OUTLINE_NORMAL_CLOSING);

        ExportFilters(builder, feedID);
        final boolean saveAbstract = !TRUE.equals( GetBoolText( cursor, 4 ) );
        ExportEntries(builder, feedID, saveAbstract);
        builder.append(OUTLINE_END);
    }

    private static final String[] ENTRIES_PROJECTION = new String[]{EntryColumns.TITLE, EntryColumns.LINK,
            EntryColumns.IS_NEW, EntryColumns.IS_READ, EntryColumns.SCROLL_POS, EntryColumns.ABSTRACT,
            EntryColumns.AUTHOR, EntryColumns.DATE, EntryColumns.FETCH_DATE, EntryColumns.IMAGE_URL, EntryColumns.IS_FAVORITE };


    private static String GetBoolText(Cursor cur, int col) {
        return cur.getInt(col) == 1 ? TRUE : FALSE;
    }
    private static boolean GetBool(Attributes attr, String attrName) {
        return TRUE.equals( attr.getValue( "", attrName ) );
    }


    private static void ExportEntries(StringBuilder builder, String feedID, boolean saveAbstract) {
        Cursor cur = MainApplication.getContext().getContentResolver()
                .query(ENTRIES_FOR_FEED_CONTENT_URI( feedID ), ENTRIES_PROJECTION, null, null, null);
        if (cur.getCount() != 0) {
            while (cur.moveToNext()) {
                builder.append("\t");
                builder.append(String.format( TAG_START, TAG_ENTRY, EntryColumns.TITLE) );
                builder.append(cur.isNull( 0 ) ? "" : TextUtils.htmlEncode(cur.getString(0)));
                builder.append(String.format( ATTR_VALUE, EntryColumns.LINK) );
                builder.append(cur.isNull( 1 ) ? "" : TextUtils.htmlEncode(cur.getString(1)));
                builder.append(String.format( ATTR_VALUE, EntryColumns.IS_NEW) );
                builder.append(GetBoolText( cur, 2));
                builder.append(String.format( ATTR_VALUE, EntryColumns.IS_READ) );
                builder.append(GetBoolText( cur, 3));
                builder.append(String.format( ATTR_VALUE, EntryColumns.SCROLL_POS) );
                builder.append(cur.getString(4));
                if ( saveAbstract ) {
                    builder.append(String.format(ATTR_VALUE, EntryColumns.ABSTRACT));
                    builder.append(cur.isNull(5) ? "" : TextUtils.htmlEncode(cur.getString(5)));
                }
                builder.append(String.format( ATTR_VALUE, EntryColumns.AUTHOR) );
                builder.append(cur.isNull( 6 ) ? "" : TextUtils.htmlEncode(cur.getString(6)));
                builder.append(String.format( ATTR_VALUE, EntryColumns.DATE) );
                builder.append(cur.getString(7));
                builder.append(String.format( ATTR_VALUE, EntryColumns.FETCH_DATE) );
                builder.append(cur.getString(8));
                builder.append(String.format( ATTR_VALUE, EntryColumns.IMAGE_URL) );
                builder.append(cur.isNull( 9 ) ? "" : TextUtils.htmlEncode(cur.getString(9)));
                builder.append(String.format( ATTR_VALUE, EntryColumns.IS_FAVORITE) );
                builder.append(GetBoolText( cur, 10));
                builder.append(TAG_CLOSING);
            }
            builder.append("\t");
        }
        cur.close();
    }

    private static final String[] FILTERS_PROJECTION = new String[]{FilterColumns.FILTER_TEXT, FilterColumns.IS_REGEX,
            FilterColumns.IS_APPLIED_TO_TITLE, FilterColumns.IS_ACCEPT_RULE, FilterColumns.IS_MARK_STARRED};

    public static void ExportFilters(StringBuilder builder, String feedID) {
        Cursor cur = MainApplication.getContext().getContentResolver()
                .query(FilterColumns.FILTERS_FOR_FEED_CONTENT_URI(feedID), FILTERS_PROJECTION, null, null, null);
        if (cur.getCount() != 0) {
            while (cur.moveToNext()) {
                builder.append("\t");
                builder.append(FILTER_TEXT);
                builder.append(TextUtils.htmlEncode(cur.getString(0)));
                builder.append(FILTER_IS_REGEX);
                builder.append(GetBoolText( cur, 1));
                builder.append(FILTER_IS_APPLIED_TO_TITLE);
                builder.append(GetBoolText( cur, 2) );
                builder.append(FILTER_IS_ACCEPT_RULE);
                builder.append(GetBoolText( cur, 3) );
                builder.append(FILTER_IS_MARK_AS_STARRED);
                builder.append(GetBoolText( cur, 4) );
                builder.append(FILTER_CLOSING);
            }
            builder.append("\t");
        }
        cur.close();
    }

    private static final String PREF_CLASS_FLOAT = "Float";
    private static final String PREF_CLASS_LONG = "Long";
    private static final String PREF_CLASS_INTEGER = "Integer";
    private static final String PREF_CLASS_BOOLEAN = "Boolean";
    private static final String PREF_CLASS_STRING = "String";

    static void SaveSettings(StringBuilder result, final String prefix) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApplication.getContext());
        for (final Map.Entry<String, ?> entry : settings.getAll().entrySet()) {
            String prefClass = entry.getValue().getClass().getName();
            String prefValue;
            if (prefClass.contains(PREF_CLASS_STRING)) {
                prefClass = PREF_CLASS_STRING;
                prefValue = ((String) entry.getValue()).replace("\n", "\\n");
            } else if (prefClass.contains(PREF_CLASS_BOOLEAN)) {
                prefClass = PREF_CLASS_BOOLEAN;
                prefValue = String.valueOf((Boolean) entry.getValue());
            } else if (prefClass.contains(PREF_CLASS_INTEGER)) {
                prefClass = PREF_CLASS_INTEGER;
                prefValue = String.valueOf((Integer) entry.getValue());
            } else if (prefClass.contains(PREF_CLASS_LONG)) {
                prefClass = PREF_CLASS_LONG;
                prefValue = String.valueOf((Long) entry.getValue());
            } else if (prefClass.contains(PREF_CLASS_FLOAT)) {
                prefClass = PREF_CLASS_FLOAT;
                prefValue = String.valueOf((Float) entry.getValue());
            } else
                continue;
            result.append(prefix + String.format( "<%s %s='%s' %s='%s' %s='%s'/>\n", TAG_PREF,
                    ATTR_PREF_CLASSNAME, prefClass,
                    ATTR_PREF_KEY, entry.getKey(),
                    ATTR_PREF_VALUE, TextUtils.htmlEncode( prefValue ) ) );
        }

    }

    private static class OPMLParser extends DefaultHandler {
        private static final String TAG_BODY = "body";
        private static final String TAG_OUTLINE = "outline";
        private static final String ATTRIBUTE_TITLE = "title";
        private static final String ATTRIBUTE_XMLURL = "xmlUrl";
        private static final String ATTRIBUTE_RETRIEVE_FULLTEXT = "retrieveFullText";
        private static final String TAG_FILTER = "filter";
        private static final String ATTRIBUTE_TEXT = "text";
        private static final String ATTRIBUTE_IS_REGEX = "isRegex";
        private static final String ATTRIBUTE_IS_APPLIED_TO_TITLE = "isAppliedToTitle";
        private static final String ATTRIBUTE_IS_ACCEPT_RULE = "isAcceptRule";
        private static final String ATTRIBUTE_IS_MARK_AS_STARRED = "isMarkAsStarred";
        private final SharedPreferences.Editor mEditor;

        private boolean mBodyTagEntered = false;
        private boolean mFeedEntered = false;
        private boolean mProbablyValidElement = false;
        private String mGroupId = null;
        private String mFeedId = null;

        public OPMLParser() {
            mEditor = PreferenceManager.getDefaultSharedPreferences( MainApplication.getContext() ).edit();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (!mBodyTagEntered) {
                if (TAG_BODY.equals(localName)) {
                    mBodyTagEntered = true;
                    mProbablyValidElement = true;
                }
            } else if (TAG_OUTLINE.equals(localName)) {
                String url = attributes.getValue("", ATTRIBUTE_XMLURL);
                String title = attributes.getValue("", ATTRIBUTE_TITLE);
                if(title == null) {
                    title = attributes.getValue("", ATTRIBUTE_TEXT);
                }

                ContentResolver cr = MainApplication.getContext().getContentResolver();

                if (url == null) { // No url => this is a group
                    if (title != null) {
                        ContentValues values = new ContentValues();
                        values.put(FeedColumns.IS_GROUP, true);
                        values.put(FeedColumns.NAME, title);

                        Cursor cursor = cr.query(FeedColumns.GROUPS_CONTENT_URI, null, FeedColumns.NAME + Constants.DB_ARG, new String[]{title}, null);

                        if (!cursor.moveToFirst()) {
                            mGroupId = cr.insert(FeedColumns.GROUPS_CONTENT_URI, values).getLastPathSegment();
                        }
                        cursor.close();
                    }

                } else { // Url => this is a feed
                    mFeedEntered = true;
                    ContentValues values = new ContentValues();

                    values.put(FeedColumns.URL, url);
                    values.put(FeedColumns.NAME, title != null && title.length() > 0 ? title : null);
                    if (mGroupId != null) {
                        values.put(FeedColumns.GROUP_ID, mGroupId);
                    }

                    values.put(FeedColumns.RETRIEVE_FULLTEXT, GetBool( attributes, ATTRIBUTE_RETRIEVE_FULLTEXT));
                    values.put(FeedColumns.SHOW_TEXT_IN_ENTRY_LIST, GetBool( attributes, FeedColumns.SHOW_TEXT_IN_ENTRY_LIST));
                    values.put(FeedColumns.IS_AUTO_REFRESH, GetBool( attributes, FeedColumns.IS_AUTO_REFRESH));
                    values.put(FeedColumns.IS_IMAGE_AUTO_LOAD, GetBool( attributes, FeedColumns.IS_IMAGE_AUTO_LOAD));
                    values.put(FeedColumns.OPTIONS, GetText( attributes, FeedColumns.OPTIONS));

                    Cursor cursor = cr.query(FeedColumns.CONTENT_URI, null, FeedColumns.URL + Constants.DB_ARG,
                            new String[]{url}, null);
                    mFeedId = null;
                    if (!cursor.moveToFirst()) {
                        mFeedId = cr.insert(FeedColumns.CONTENT_URI, values).getLastPathSegment();
                    }
                    cursor.close();
                }
            } else if (TAG_FILTER.equals(localName)) {
                if (mFeedEntered && mFeedId != null) {
                    ContentValues values = new ContentValues();
                    values.put(FilterColumns.FILTER_TEXT, attributes.getValue("", ATTRIBUTE_TEXT));
                    values.put(FilterColumns.IS_REGEX, TRUE.equals(attributes.getValue("", ATTRIBUTE_IS_REGEX)));
                    values.put(FilterColumns.IS_APPLIED_TO_TITLE, TRUE.equals(attributes.getValue("", ATTRIBUTE_IS_APPLIED_TO_TITLE)));
                    values.put(FilterColumns.IS_ACCEPT_RULE, TRUE.equals(attributes.getValue("", ATTRIBUTE_IS_ACCEPT_RULE)));
                    values.put(FilterColumns.IS_MARK_STARRED, TRUE.equals(attributes.getValue("", ATTRIBUTE_IS_MARK_AS_STARRED)));

                    ContentResolver cr = MainApplication.getContext().getContentResolver();
                    cr.insert(FilterColumns.FILTERS_FOR_FEED_CONTENT_URI(mFeedId), values);
                }
            } else if (TAG_ENTRY.equals(localName)) {
                if (mFeedEntered && mFeedId != null) {
                    ContentValues values = new ContentValues();
                    values.put(EntryColumns.IS_NEW, GetBool( attributes, EntryColumns.IS_NEW));
                    values.put(EntryColumns.IS_READ, GetBool( attributes, EntryColumns.IS_READ));
                    values.put(EntryColumns.IS_FAVORITE, GetBool( attributes, EntryColumns.IS_FAVORITE));
                    values.put(EntryColumns.LINK, GetText( attributes, EntryColumns.LINK ));
                    values.put(EntryColumns.ABSTRACT, GetText( attributes, EntryColumns.ABSTRACT));
                    values.put(EntryColumns.FETCH_DATE, GetText( attributes, EntryColumns.FETCH_DATE));
                    values.put(EntryColumns.DATE, GetText( attributes, EntryColumns.DATE));
                    values.put(EntryColumns.TITLE, GetText( attributes, EntryColumns.TITLE));
                    values.put(EntryColumns.SCROLL_POS, GetText( attributes, EntryColumns.SCROLL_POS ));
                    values.put(EntryColumns.AUTHOR, GetText( attributes, EntryColumns.AUTHOR) );
                    values.put(EntryColumns.IMAGE_URL, GetText( attributes, EntryColumns.IMAGE_URL));

                    ContentResolver cr = MainApplication.getContext().getContentResolver();
                    cr.insert(EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI( mFeedId ), values);
                }
            } else if (TAG_PREF.equals(localName)) {
                final String className = attributes.getValue( ATTR_PREF_CLASSNAME );
                final String value = attributes.getValue( ATTR_PREF_VALUE);
                final String key = attributes.getValue( ATTR_PREF_KEY );
                if (className.contains(PREF_CLASS_STRING)) {
                    mEditor.putString(key, value.replace("\\n", "\n"));
                } else if (className.contains(PREF_CLASS_BOOLEAN)) {
                    mEditor.putBoolean(key, Boolean.parseBoolean(value));
                } else if (className.contains(PREF_CLASS_INTEGER)) {
                    mEditor.putInt(key, Integer.parseInt(value));
                } else if (className.contains(PREF_CLASS_LONG)) {
                    mEditor.putLong(key, Long.parseLong(value));
                } else if (className.contains(PREF_CLASS_FLOAT)) {
                    mEditor.putFloat(key, Float.parseFloat(value));
                } else {
                    // throw new ClassNotFoundException("Unknown type: "
                    // + prefClass);
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (mBodyTagEntered && TAG_BODY.equals(localName)) {
                mBodyTagEntered = false;
            } else if (TAG_OUTLINE.equals(localName)) {
                if (mFeedEntered) {
                    mFeedEntered = false;
                } else {
                    mGroupId = null;
                }
            }
        }

        @Override
        public void warning(SAXParseException e) {
            // ignore warnings
        }

        @Override
        public void error(SAXParseException e) {
            // ignore small errors
        }

        @Override
        public void endDocument() throws SAXException {
            if (!mProbablyValidElement) {
                throw new SAXException();
            } else {
                super.endDocument();
            }
        }
    }
}
