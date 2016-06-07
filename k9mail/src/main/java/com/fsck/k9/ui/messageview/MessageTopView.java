package com.fsck.k9.ui.messageview;


import java.util.ArrayList;
import java.util.List;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.fsck.k9.Account;
import com.fsck.k9.Account.ShowPictures;
import com.fsck.k9.K9;
import com.fsck.k9.R;
import com.fsck.k9.helper.Contacts;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mailstore.MessageViewInfo;
import com.fsck.k9.ui.messageview.MessageContainerView.OnRenderingFinishedListener;
import com.fsck.k9.view.MessageCryptoDisplayStatus;
import com.fsck.k9.view.MessageHeader;
import com.fsck.k9.view.ToolableViewAnimator;
import org.openintents.openpgp.OpenPgpError;


public class MessageTopView extends LinearLayout implements ShowPicturesController {

    public static final int PROGRESS_MAX = 1000;
    public static final int PROGRESS_MAX_WITH_MARGIN = 950;
    public static final int PROGRESS_STEP_DURATION = 180;


    private ToolableViewAnimator viewAnimator;
    private ProgressBar progressBar;
    private TextView progressText;

    private MessageHeader mHeaderContainer;
    private LayoutInflater mInflater;
    private FrameLayout containerView;
    private Button mDownloadRemainder;
    private AttachmentViewCallback attachmentCallback;
    private Button showPicturesButton;
    private List<MessageContainerView> messageContainerViewsWithPictures = new ArrayList<>();
    private boolean isShowingProgress;

    private MessageCryptoPresenter messageCryptoPresenter;


    public MessageTopView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        mHeaderContainer = (MessageHeader) findViewById(R.id.header_container);
        // mHeaderContainer.setOnLayoutChangedListener(this);
        mInflater = LayoutInflater.from(getContext());

        viewAnimator = (ToolableViewAnimator) findViewById(R.id.message_layout_animator);
        progressBar = (ProgressBar) findViewById(R.id.message_progress);
        progressText = (TextView) findViewById(R.id.message_progress_text);

        mDownloadRemainder = (Button) findViewById(R.id.download_remainder);
        mDownloadRemainder.setVisibility(View.GONE);

        showPicturesButton = (Button) findViewById(R.id.show_pictures);
        setShowPicturesButtonListener();

        containerView = (FrameLayout) findViewById(R.id.message_container);

        hideHeaderView();
    }

    private void setShowPicturesButtonListener() {
        showPicturesButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showPicturesInAllContainerViews();
            }
        });
    }

    private void showPicturesInAllContainerViews() {
        for (MessageContainerView containerView : messageContainerViewsWithPictures) {
            containerView.showPictures();
        }

        hideShowPicturesButton();
    }

    public void resetView() {
        mDownloadRemainder.setVisibility(View.GONE);
        containerView.removeAllViews();
    }

    public void setMessage(Account account, MessageViewInfo messageViewInfo) throws MessagingException {
        resetView();

        MessageCryptoDisplayStatus displayStatus =
                MessageCryptoDisplayStatus.fromResultAnnotation(messageViewInfo.cryptoResultAnnotation);
        mHeaderContainer.setCryptoStatus(displayStatus);

        switch (displayStatus) {
            case DISABLED:
            case INCOMPLETE_SIGNED:
            case UNSUPPORTED_SIGNED:
            default: {
                // in most cases, we simply display the message
                showMessageContentView(account, messageViewInfo);
                break;
            }

            case CANCELLED: {
                showMessageCryptoCancelledView(account);
                break;
            }

            case INCOMPLETE_ENCRYPTED: {
                showEncryptedButIncompleteView(account);
                break;
            }

            case ENCRYPTED_ERROR:
            case UNSUPPORTED_ENCRYPTED: {
                showMessageCryptoErrorView(account, messageViewInfo);
                break;
            }

            case LOADING: {
                throw new IllegalStateException("Displaying message while in loading state!");
            }
        }
    }

    private void showEncryptedButIncompleteView(Account account) {
        View view = mInflater.inflate(R.layout.message_content_crypto_incomplete, containerView, false);

        ImageView cryptoProviderIcon = (ImageView) view.findViewById(R.id.crypto_error_icon);
        setCryptoProviderIcon(account, cryptoProviderIcon);

        containerView.addView(view);
        displayViewOnLoadFinished(false);
    }

    private void showMessageContentView(Account account, MessageViewInfo messageViewInfo)
            throws MessagingException {
        ShowPictures showPicturesSetting = account.getShowPictures();
        boolean automaticallyLoadPictures =
                shouldAutomaticallyLoadPictures(showPicturesSetting, messageViewInfo.message);

        MessageContainerView view = (MessageContainerView) mInflater.inflate(R.layout.message_container,
                containerView, false);
        containerView.addView(view);

        view.displayMessageViewContainer(messageViewInfo, new OnRenderingFinishedListener() {
            @Override
            public void onLoadFinished() {
                displayViewOnLoadFinished(true);
            }
        }, automaticallyLoadPictures, this, attachmentCallback);
    }

    private void showMessageCryptoErrorView(Account account, MessageViewInfo messageViewInfo) {
        View view = mInflater.inflate(R.layout.message_content_crypto_error, containerView, false);

        ImageView cryptoProviderIcon = (ImageView) view.findViewById(R.id.crypto_error_icon);
        setCryptoProviderIcon(account, cryptoProviderIcon);

        TextView cryptoErrorText = (TextView) view.findViewById(R.id.crypto_error_text);
        OpenPgpError openPgpError = messageViewInfo.cryptoResultAnnotation.getOpenPgpError();
        if (openPgpError != null) {
            String errorText = openPgpError.getMessage();
            cryptoErrorText.setText(errorText);
        }

        containerView.addView(view);
        displayViewOnLoadFinished(false);
    }

    private void showMessageCryptoCancelledView(Account account) {
        View view = mInflater.inflate(R.layout.message_content_crypto_cancelled, containerView, false);
        view.findViewById(R.id.crypto_cancelled_retry).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                messageCryptoPresenter.onClickRetryCryptoOperation();
            }
        });

        ImageView cryptoProviderIcon = (ImageView) view.findViewById(R.id.crypto_error_icon);
        setCryptoProviderIcon(account, cryptoProviderIcon);

        containerView.addView(view);
        displayViewOnLoadFinished(false);
    }

    private void setCryptoProviderIcon(Account account, ImageView cryptoProviderIcon) {
        Drawable openPgpApiProviderIcon = MessageCryptoPresenter.getOpenPgpApiProviderIcon(getContext(), account);
        if (openPgpApiProviderIcon != null) {
            cryptoProviderIcon.setImageDrawable(openPgpApiProviderIcon);
        } else {
            cryptoProviderIcon.setImageResource(R.drawable.status_lock_error);
            cryptoProviderIcon.setColorFilter(getResources().getColor(R.color.openpgp_red));
        }
    }

    /**
     * Fetch the message header view.  This is not the same as the message headers; this is the View shown at the top
     * of messages.
     * @return MessageHeader View.
     */
    public MessageHeader getMessageHeaderView() {
        return mHeaderContainer;
    }

    public void setHeaders(final Message message, Account account) {
        try {
            mHeaderContainer.populate(message, account);
            if (account.isOpenPgpProviderConfigured()) {
                mHeaderContainer.setCryptoStatus(MessageCryptoDisplayStatus.LOADING);
            }
            mHeaderContainer.setVisibility(View.VISIBLE);

        } catch (Exception me) {
            Log.e(K9.LOG_TAG, "setHeaders - error", me);
        }
    }

    public void setOnToggleFlagClickListener(OnClickListener listener) {
        mHeaderContainer.setOnFlagListener(listener);
    }

    public void showAllHeaders() {
        mHeaderContainer.onShowAdditionalHeaders();
    }

    public boolean additionalHeadersVisible() {
        return mHeaderContainer.additionalHeadersVisible();
    }

    private void hideHeaderView() {
        mHeaderContainer.setVisibility(View.GONE);
    }

    public void setOnDownloadButtonClickListener(OnClickListener listener) {
        mDownloadRemainder.setOnClickListener(listener);
    }

    public void setAttachmentCallback(AttachmentViewCallback callback) {
        attachmentCallback = callback;
    }

    public void setMessageCryptoPresenter(MessageCryptoPresenter messageCryptoPresenter) {
        this.messageCryptoPresenter = messageCryptoPresenter;
        mHeaderContainer.setOnCryptoClickListener(messageCryptoPresenter);
    }

    public void enableDownloadButton() {
        mDownloadRemainder.setEnabled(true);
    }

    public void disableDownloadButton() {
        mDownloadRemainder.setEnabled(false);
    }

    public void setShowDownloadButton(Message message) {
        if (message.isSet(Flag.X_DOWNLOADED_FULL)) {
            mDownloadRemainder.setVisibility(View.GONE);
        } else {
            mDownloadRemainder.setEnabled(true);
            mDownloadRemainder.setVisibility(View.VISIBLE);
        }
    }

    private void showShowPicturesButton() {
        showPicturesButton.setVisibility(View.VISIBLE);
    }

    private void hideShowPicturesButton() {
        showPicturesButton.setVisibility(View.GONE);
    }

    @Override
    public void notifyMessageContainerContainsPictures(MessageContainerView messageContainerView) {
        messageContainerViewsWithPictures.add(messageContainerView);

        showShowPicturesButton();
    }

    private boolean shouldAutomaticallyLoadPictures(ShowPictures showPicturesSetting, Message message) {
        return showPicturesSetting == ShowPictures.ALWAYS || shouldShowPicturesFromSender(showPicturesSetting, message);
    }

    private boolean shouldShowPicturesFromSender(ShowPictures showPicturesSetting, Message message) {
        if (showPicturesSetting != ShowPictures.ONLY_FROM_CONTACTS) {
            return false;
        }

        String senderEmailAddress = getSenderEmailAddress(message);
        if (senderEmailAddress == null) {
            return false;
        }

        Contacts contacts = Contacts.getInstance(getContext());
        return contacts.isInContacts(senderEmailAddress);
    }

    private String getSenderEmailAddress(Message message) {
        Address[] from = message.getFrom();
        if (from == null || from.length == 0) {
            return null;
        }

        return from[0].getAddress();
    }

    public void displayViewOnLoadFinished(boolean finishProgressBar) {
        if (!finishProgressBar || !isShowingProgress) {
            viewAnimator.setDisplayedChild(2);
            return;
        }

        ObjectAnimator animator = ObjectAnimator.ofInt(
                progressBar, "progress", progressBar.getProgress(), PROGRESS_MAX);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                viewAnimator.setDisplayedChild(2);
            }
        });
        animator.setDuration(PROGRESS_STEP_DURATION);
        animator.start();
    }

    public void setToLoadingState() {
        viewAnimator.setDisplayedChild(0);
        progressBar.setProgress(0);
        isShowingProgress = false;
    }

    public void setLoadingProgress(int progress, int max) {
        if (!isShowingProgress) {
            viewAnimator.setDisplayedChild(1);
            isShowingProgress = true;
            return;
        }

        int newposition = (int) (progress / (float) max * PROGRESS_MAX_WITH_MARGIN);
        int currentPosition = progressBar.getProgress();
        if (newposition > currentPosition) {
            ObjectAnimator.ofInt(progressBar, "progress", currentPosition, newposition)
                    .setDuration(PROGRESS_STEP_DURATION).start();
        } else {
            progressBar.setProgress(newposition);
        }
    }
}
