package com.example.matrixchat.utils;

import android.net.Uri;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.method.Touch;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.widget.TextView;


// This class tracks the link click on textview
public class MatrixLinkMovementMethod extends LinkMovementMethod {

    private IMessagesAdapterActionsListener mListener;

    public MatrixLinkMovementMethod(IMessagesAdapterActionsListener listener) {
        mListener = listener;
    }

    public void updateListener(IMessagesAdapterActionsListener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getAction();

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();

            x += widget.getScrollX();
            y += widget.getScrollY();

            Layout layout = widget.getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);

            ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);

            if (link.length != 0) {
                if (action == MotionEvent.ACTION_UP) {
                    if (link[0] instanceof URLSpan) {
                        if (null != mListener) {
                            URLSpan span = (URLSpan) link[0];
                            mListener.onURLClick(Uri.parse(span.getURL()));
                        }
                    } else {
                        link[0].onClick(widget);
                    }

                } else if (action == MotionEvent.ACTION_DOWN) {
                    Selection.setSelection(buffer,
                            buffer.getSpanStart(link[0]),
                            buffer.getSpanEnd(link[0]));
                }

                return true;
            } else {
                Selection.removeSelection(buffer);
                Touch.onTouchEvent(widget, buffer, event);
                return false;
            }
        }
        return Touch.onTouchEvent(widget, buffer, event);
    }
}
