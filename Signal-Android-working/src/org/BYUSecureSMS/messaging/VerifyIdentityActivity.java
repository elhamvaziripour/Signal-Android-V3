/**
 * Copyright (C) 2016 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.BYUSecureSMS.messaging;

import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SwitchCompat;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.BYUSecureSMS.messaging.color.MaterialColor;
import org.BYUSecureSMS.messaging.crypto.IdentityKeyParcelable;
import org.BYUSecureSMS.messaging.crypto.IdentityKeyUtil;
import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.crypto.MasterSecretUnion;
import org.BYUSecureSMS.messaging.database.Address;
import org.BYUSecureSMS.messaging.database.IdentityDatabase;
import org.BYUSecureSMS.messaging.jobs.MultiDeviceVerifiedUpdateJob;
import org.BYUSecureSMS.messaging.qr.QrCode;
import org.BYUSecureSMS.messaging.qr.ScanningThread;
import org.BYUSecureSMS.messaging.util.DynamicLanguage;
import org.BYUSecureSMS.messaging.util.DynamicTheme;
import org.BYUSecureSMS.messaging.util.IdentityUtil;
import org.BYUSecureSMS.messaging.util.Util;
import org.BYUSecureSMS.messaging.components.camera.CameraView;
import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.qr.ScanListener;
import org.BYUSecureSMS.messaging.recipients.Recipient;
import org.BYUSecureSMS.messaging.recipients.RecipientFactory;
import org.BYUSecureSMS.messaging.util.TextSecurePreferences;
import org.BYUSecureSMS.messaging.util.ViewUtil;
import org.BYUSecureSMS.messaging.util.concurrent.ListenableFuture;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.libsignal.fingerprint.FingerprintParsingException;
import org.whispersystems.libsignal.fingerprint.FingerprintVersionMismatchException;
import org.whispersystems.libsignal.fingerprint.NumericFingerprintGenerator;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;

import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

/**
 * Activity for verifying identity keys.
 *
 * @author Moxie Marlinspike
 */
public class VerifyIdentityActivity extends PassphraseRequiredActionBarActivity implements Recipient.RecipientModifiedListener, ScanListener, View.OnClickListener {

  private static final String TAG = VerifyIdentityActivity.class.getSimpleName();

  public static final String ADDRESS_EXTRA  = "address";
  public static final String IDENTITY_EXTRA = "recipient_identity";
  public static final String VERIFIED_EXTRA = "verified_state";

  private final DynamicTheme dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private VerifyDisplayFragment displayFragment = new VerifyDisplayFragment();
  private VerifyScanFragment    scanFragment    = new VerifyScanFragment();
  private Recipient recipient;
  private MasterSecret masterSecrets;
  @Override
  public void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }
  @Override
  public void onBackPressed() {
    super.onBackPressed();
    //    finish();

  }
  @Override
  protected void onCreate(Bundle state, @NonNull MasterSecret masterSecret) {
    this.masterSecrets = masterSecret;
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setTitle(R.string.AndroidManifest__verify_safety_number);

    this.recipient = RecipientFactory.getRecipientFor(this, (Address)getIntent().getParcelableExtra(ADDRESS_EXTRA), true);
    recipient.addListener(this);

    setActionBarNotificationBarColor(recipient.getColor());

    Bundle extras = new Bundle();
    extras.putParcelable(VerifyDisplayFragment.REMOTE_ADDRESS, getIntent().getParcelableExtra(ADDRESS_EXTRA));
    extras.putParcelable(VerifyDisplayFragment.REMOTE_IDENTITY, getIntent().getParcelableExtra(IDENTITY_EXTRA));
    extras.putString(VerifyDisplayFragment.REMOTE_NUMBER, recipient.getAddress().toPhoneString());
    extras.putParcelable(VerifyDisplayFragment.LOCAL_IDENTITY, new IdentityKeyParcelable(IdentityKeyUtil.getIdentityKey(this)));
    extras.putString(VerifyDisplayFragment.LOCAL_NUMBER, TextSecurePreferences.getLocalNumber(this));
    extras.putBoolean(VerifyDisplayFragment.VERIFIED_STATE, getIntent().getBooleanExtra(VERIFIED_EXTRA, false));


    extras.putParcelable(VerifyScanFragment.REMOTE_ADDRESS, getIntent().getParcelableExtra(ADDRESS_EXTRA));
    extras.putParcelable(VerifyScanFragment.REMOTE_IDENTITY, getIntent().getParcelableExtra(IDENTITY_EXTRA));
    extras.putString(VerifyScanFragment.REMOTE_NUMBER, recipient.getAddress().toPhoneString());
    extras.putParcelable(VerifyScanFragment.LOCAL_IDENTITY, new IdentityKeyParcelable(IdentityKeyUtil.getIdentityKey(this)));
    extras.putString(VerifyScanFragment.LOCAL_NUMBER, TextSecurePreferences.getLocalNumber(this));
    extras.putBoolean(VerifyScanFragment.VERIFIED_STATE, getIntent().getBooleanExtra(VERIFIED_EXTRA, false));

    scanFragment.setScanListener(this);
    displayFragment.setClickListener(this);

   initFragment(android.R.id.content, displayFragment, masterSecret, dynamicLanguage.getCurrentLocale(), extras);
 initFragment(android.R.id.content, scanFragment, masterSecret, dynamicLanguage.getCurrentLocale(), extras);

  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home: finish(); return true;
    }

    return false;
  }

  @Override
  public void onModified(final Recipient recipient) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        setActionBarNotificationBarColor(recipient.getColor());
      }
    });
  }

  @Override
  public void onQrDataFound(final String data) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        ((Vibrator)getSystemService(Context.VIBRATOR_SERVICE)).vibrate(50);

      //  getSupportFragmentManager().popBackStack();
        //displayFragment.setScannedFingerprint(data);
        scanFragment.setScannedFingerprint(data);
        getSupportFragmentManager().popBackStack();
      }
    });
  }

  @Override
  public void onClick(View v) {
    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
    //transaction.setCustomAnimations(R.anim.slide_from_top, R.anim.slide_to_bottom,R.anim.slide_from_bottom, R.anim.slide_to_top);

    transaction.replace(android.R.id.content, scanFragment)
               .addToBackStack(null)
               .commit();
    Bundle extras = new Bundle();
    extras.putParcelable(VerifyScanFragment.REMOTE_ADDRESS, getIntent().getParcelableExtra(ADDRESS_EXTRA));
    extras.putParcelable(VerifyScanFragment.REMOTE_IDENTITY, getIntent().getParcelableExtra(IDENTITY_EXTRA));
    extras.putString(VerifyScanFragment.REMOTE_NUMBER, this.recipient.getAddress().toPhoneString());
    extras.putParcelable(VerifyScanFragment.LOCAL_IDENTITY, new IdentityKeyParcelable(IdentityKeyUtil.getIdentityKey(this)));
    extras.putString(VerifyScanFragment.LOCAL_NUMBER, TextSecurePreferences.getLocalNumber(this));
    extras.putBoolean(VerifyScanFragment.VERIFIED_STATE, getIntent().getBooleanExtra(VERIFIED_EXTRA, false));

    initFragment(android.R.id.content, scanFragment, this.masterSecrets, dynamicLanguage.getCurrentLocale(), extras);

  }

  private void setActionBarNotificationBarColor(MaterialColor color) {
    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(color.toActionBarColor(this)));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      getWindow().setStatusBarColor(color.toStatusBarColor(this));
    }
  }

  public static class VerifyDisplayFragment extends Fragment implements Recipient.RecipientModifiedListener, CompoundButton.OnCheckedChangeListener {

    public static final String REMOTE_ADDRESS  = "remote_address";
    public static final String REMOTE_NUMBER   = "remote_number";
    public static final String REMOTE_IDENTITY = "remote_identity";
    public static final String LOCAL_IDENTITY  = "local_identity";
    public static final String LOCAL_NUMBER    = "local_number";
    public static final String VERIFIED_STATE  = "verified_state";

    private MasterSecret masterSecret;
    private Recipient    recipient;
    private String       localNumber;
    private String       remoteNumber;

    private IdentityKey localIdentity;
    private IdentityKey remoteIdentity;

    private Fingerprint fingerprint;

    private View                 container;
    private View                 numbersContainer;
    private ImageView            qrCode;
    private ImageView            qrVerified;
    private TextView             tapLabel;
    private TextView             description;
    private View.OnClickListener clickListener;
    private SwitchCompat         verified;

    private TextView[] codes                = new TextView[12];
    private boolean    animateSuccessOnDraw = false;
    private boolean    animateFailureOnDraw = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
      this.container        = ViewUtil.inflate(inflater, viewGroup, R.layout.verify_display_fragment);
      this.numbersContainer = ViewUtil.findById(container, R.id.number_table);
      this.qrCode           = ViewUtil.findById(container, R.id.qr_code);
      this.verified         = ViewUtil.findById(container, R.id.verified_switch);
      this.qrVerified       = ViewUtil.findById(container, R.id.qr_verified);
      this.description      = ViewUtil.findById(container, R.id.description);
      this.tapLabel         = ViewUtil.findById(container, R.id.tap_label);
      this.codes[0]         = ViewUtil.findById(container, R.id.code_first);
      this.codes[1]         = ViewUtil.findById(container, R.id.code_second);
      this.codes[2]         = ViewUtil.findById(container, R.id.code_third);
      this.codes[3]         = ViewUtil.findById(container, R.id.code_fourth);
      this.codes[4]         = ViewUtil.findById(container, R.id.code_fifth);
      this.codes[5]         = ViewUtil.findById(container, R.id.code_sixth);
      this.codes[6]         = ViewUtil.findById(container, R.id.code_seventh);
      this.codes[7]         = ViewUtil.findById(container, R.id.code_eighth);
      this.codes[8]         = ViewUtil.findById(container, R.id.code_ninth);
      this.codes[9]         = ViewUtil.findById(container, R.id.code_tenth);
      this.codes[10]        = ViewUtil.findById(container, R.id.code_eleventh);
      this.codes[11]        = ViewUtil.findById(container, R.id.code_twelth);

      this.qrCode.setOnClickListener(clickListener);
      this.registerForContextMenu(numbersContainer);

      this.verified.setChecked(getArguments().getBoolean(VERIFIED_STATE, false));
      this.verified.setOnCheckedChangeListener(this);


      // Elham if you want to jump  to  the scanner
      //return container;
      //this.container  = ViewUtil.inflate(inflater, viewGroup, R.layout.verify_scan_fragment);
     // this.cameraView = ViewUtil.findById(container, R.id.scanner);
      this.qrCode.performClick();

      return container;
    }

    @Override
    public void onCreate(Bundle bundle) {
      super.onCreate(bundle);

      Address               address                  = getArguments().getParcelable(REMOTE_ADDRESS);
      IdentityKeyParcelable localIdentityParcelable  = getArguments().getParcelable(LOCAL_IDENTITY);
      IdentityKeyParcelable remoteIdentityParcelable = getArguments().getParcelable(REMOTE_IDENTITY);

      if (address == null)                  throw new AssertionError("Address required");
      if (localIdentityParcelable == null)  throw new AssertionError("local identity required");
      if (remoteIdentityParcelable == null) throw new AssertionError("remote identity required");

      this.masterSecret   = getArguments().getParcelable("master_secret");
      this.localNumber    = getArguments().getString(LOCAL_NUMBER);
      this.localIdentity  = localIdentityParcelable.get();
      this.remoteNumber   = getArguments().getString(REMOTE_NUMBER);
      this.recipient      = RecipientFactory.getRecipientFor(getActivity(), address, true);
      this.remoteIdentity = remoteIdentityParcelable.get();

      this.recipient.addListener(this);

      new AsyncTask<Void, Void, Fingerprint>() {
        @Override
        protected Fingerprint doInBackground(Void... params) {
          return new NumericFingerprintGenerator(5200).createFor(localNumber, localIdentity,
                                                                 remoteNumber, remoteIdentity);
        }

        @Override
        protected void onPostExecute(Fingerprint fingerprint) {
          VerifyDisplayFragment.this.fingerprint = fingerprint;
          setFingerprintViews(fingerprint, true);
          getActivity().supportInvalidateOptionsMenu();
        }
      }.execute();

      setHasOptionsMenu(true);
    }

    @Override
    public void onModified(final Recipient recipient) {
      Util.runOnMain(new Runnable() {
        @Override
        public void run() {
          setRecipientText(recipient);
        }
      });
    }

    @Override
    public void onResume() {
      super.onResume();

      setRecipientText(recipient);

      if (fingerprint != null) {
        setFingerprintViews(fingerprint, false);
      }

      if (animateSuccessOnDraw) {
        animateSuccessOnDraw = false;
        animateVerifiedSuccess();
      } else if (animateFailureOnDraw) {
        animateFailureOnDraw = false;
        animateVerifiedFailure();
      }
    }

    @Override
    public void onDestroy() {
      super.onDestroy();
      recipient.removeListener(this);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
                                    ContextMenuInfo menuInfo)
    {
      super.onCreateContextMenu(menu, view, menuInfo);

      if (fingerprint != null) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.verify_display_fragment_context_menu, menu);
      }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
      if (fingerprint == null) return super.onContextItemSelected(item);

      switch (item.getItemId()) {
        case R.id.menu_copy:    handleCopyToClipboard(fingerprint, codes.length); return true;
        case R.id.menu_compare: handleCompareWithClipboard(fingerprint);          return true;
        default:                return super.onContextItemSelected(item);
      }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
      super.onCreateOptionsMenu(menu, inflater);

      if (fingerprint != null) {
        inflater.inflate(R.menu.verify_identity, menu);
      }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
      switch (item.getItemId()) {
        case R.id.verify_identity__share: handleShare(fingerprint, codes.length);  return true;
      }

      return false;
    }

    public void setScannedFingerprint(String scanned) {
      try {


        if (this.fingerprint.getScannableFingerprint().compareTo(scanned.getBytes("ISO-8859-1"))) {
          this.animateSuccessOnDraw = true;

          // Elham edit starts
          // If the Qr code scanning was successful

          Log.w(TAG, "Saving identity QR code scan data: " + this.recipient.getAddress());
          DatabaseFactory.getIdentityDatabase(getActivity())
                  .saveIdentity(this.recipient.getAddress(),
                          remoteIdentity,
                          IdentityDatabase.VerifiedStatus.VERIFIED, false,
                          System.currentTimeMillis(), true);
          // Should make animation
          this.verified.setOnCheckedChangeListener(null);
          this.verified.setChecked(true);
          this.verified.setOnCheckedChangeListener(this);
          Toast.makeText(getContext(), "Congratulations!\nYour conversation is secure now.",
                  Toast.LENGTH_LONG).show();


          // Elham edit Ends
        } else {
          this.animateFailureOnDraw = true;

          // Elham edit starts
          // If the Qr code scaning was not successful

          Log.w(TAG, "Remove identity: " + this.recipient.getAddress());
          DatabaseFactory.getIdentityDatabase(getActivity())
                  .setVerified(this.recipient.getAddress(),
                          remoteIdentity,
                          IdentityDatabase.VerifiedStatus.DEFAULT);

          Toast.makeText(getContext(), "Failed to verify!\nYour conversation is not secure.",
                  Toast.LENGTH_LONG).show();

          // Should make animation
          this.verified.setOnCheckedChangeListener(null);
          this.verified.setChecked(false);
          this.verified.setOnCheckedChangeListener(this);

          // Elham edit Ends
        }
      } catch (FingerprintVersionMismatchException e) {
        Log.w(TAG, e);
        if (e.getOurVersion() < e.getTheirVersion()) {
          Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_your_contact_is_running_a_newer_version_of_Signal, Toast.LENGTH_LONG).show();
        } else {
          Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_your_contact_is_running_an_old_version_of_signal, Toast.LENGTH_LONG).show();
        }
      } catch (FingerprintParsingException e) {
        Log.w(TAG, e);
        Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_the_scanned_qr_code_is_not_a_correctly_formatted_safety_number, Toast.LENGTH_LONG).show();
      } catch (UnsupportedEncodingException e) {
        throw new AssertionError(e);
      }
    }

    public void setClickListener(View.OnClickListener listener) {
      this.clickListener = listener;
    }

    private @NonNull String getFormattedSafetyNumbers(@NonNull Fingerprint fingerprint, int segmentCount) {
      String[]      segments = getSegments(fingerprint, segmentCount);
      StringBuilder result   = new StringBuilder();

      for (int i = 0; i < segments.length; i++) {
        result.append(segments[i]);

        if (i != segments.length - 1) {
          if (((i+1) % 4) == 0) result.append('\n');
          else                  result.append(' ');
        }
      }

      return result.toString();
    }

    private void handleCopyToClipboard(Fingerprint fingerprint, int segmentCount) {
      Util.writeTextToClipboard(getActivity(), getFormattedSafetyNumbers(fingerprint, segmentCount));
    }

    private void handleCompareWithClipboard(Fingerprint fingerprint) {
      String clipboardData = Util.readTextFromClipboard(getActivity());

      if (clipboardData == null) {
        Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_no_safety_number_to_compare_was_found_in_the_clipboard, Toast.LENGTH_LONG).show();
        return;
      }

      String numericClipboardData = clipboardData.replaceAll("\\D", "");

      if (TextUtils.isEmpty(numericClipboardData) || numericClipboardData.length() != 60) {
        Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_no_safety_number_to_compare_was_found_in_the_clipboard, Toast.LENGTH_LONG).show();
        return;
      }

      if (fingerprint.getDisplayableFingerprint().getDisplayText().equals(numericClipboardData)) {
        animateVerifiedSuccess();
      } else {
        animateVerifiedFailure();
      }
    }

    private void handleShare(@NonNull Fingerprint fingerprint, int segmentCount) {
      String shareString =
          getString(R.string.VerifyIdentityActivity_our_signal_safety_number) + "\n" +
              getFormattedSafetyNumbers(fingerprint, segmentCount) + "\n";

      Intent intent = new Intent();
      intent.setAction(Intent.ACTION_SEND);
      intent.putExtra(Intent.EXTRA_TEXT, shareString);
      intent.setType("text/plain");

      try {
        startActivity(Intent.createChooser(intent, getString(R.string.VerifyIdentityActivity_share_safety_number_via)));
      } catch (ActivityNotFoundException e) {
        Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_no_app_to_share_to, Toast.LENGTH_LONG).show();
      }
    }

    private void setRecipientText(Recipient recipient) {
    description.setText(Html.fromHtml(String.format(getActivity().getString(R.string.verify_display_fragment__if_you_wish_to_verify_the_security_of_your_end_to_end_encryption_with_s), recipient.toShortString())));
     description.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void setFingerprintViews(Fingerprint fingerprint, boolean animate) {
      String[] segments = getSegments(fingerprint, codes.length);

      for (int i=0;i<codes.length;i++) {
        if (animate) setCodeSegment(codes[i], segments[i]);
        else         codes[i].setText(segments[i]);
      }

      byte[] qrCodeData   = fingerprint.getScannableFingerprint().getSerialized();
      String qrCodeString = new String(qrCodeData, Charset.forName("ISO-8859-1"));
      Bitmap qrCodeBitmap = QrCode.create(qrCodeString);

      qrCode.setImageBitmap(qrCodeBitmap);

      if (animate) {
        ViewUtil.fadeIn(qrCode, 1000);
        ViewUtil.fadeIn(tapLabel, 1000);
      } else {
        qrCode.setVisibility(View.VISIBLE);
        tapLabel.setVisibility(View.VISIBLE);
      }
    }

    private void setCodeSegment(final TextView codeView, String segment) {
      if (Build.VERSION.SDK_INT >= 11) {
        ValueAnimator valueAnimator = new ValueAnimator();
        valueAnimator.setObjectValues(0, Integer.parseInt(segment));

        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
          @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
          @Override
          public void onAnimationUpdate(ValueAnimator animation) {
            int value = (int) animation.getAnimatedValue();
            codeView.setText(String.format("%05d", value));
          }
        });

        valueAnimator.setEvaluator(new TypeEvaluator<Integer>() {
          public Integer evaluate(float fraction, Integer startValue, Integer endValue) {
            return Math.round(startValue + (endValue - startValue) * fraction);
          }
        });

        valueAnimator.setDuration(1000);
        valueAnimator.start();
      } else {
        codeView.setText(segment);
      }
    }

    private String[] getSegments(Fingerprint fingerprint, int segmentCount) {
      String[] segments = new String[segmentCount];
      String   digits   = fingerprint.getDisplayableFingerprint().getDisplayText();
      int      partSize = digits.length() / segmentCount;

      for (int i=0;i<segmentCount;i++) {
        segments[i] = digits.substring(i * partSize, (i * partSize) + partSize);
      }

      return segments;
    }

    private Bitmap createVerifiedBitmap(int width, int height, @DrawableRes int id) {
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      Bitmap check  = BitmapFactory.decodeResource(getResources(), id);
      float  offset = (width - check.getWidth()) / 2;

      canvas.drawBitmap(check, offset, offset, null);

      return bitmap;
    }

    private void animateVerifiedSuccess() {
      Bitmap qrBitmap  = ((BitmapDrawable)qrCode.getDrawable()).getBitmap();
      Bitmap qrSuccess = createVerifiedBitmap(qrBitmap.getWidth(), qrBitmap.getHeight(), R.drawable.ic_check_white_48dp);

      qrVerified.setImageBitmap(qrSuccess);
      qrVerified.getBackground().setColorFilter(getResources().getColor(R.color.green_500), PorterDuff.Mode.MULTIPLY);

      animateVerified();
    }

    private void animateVerifiedFailure() {
      Bitmap qrBitmap  = ((BitmapDrawable)qrCode.getDrawable()).getBitmap();
      Bitmap qrSuccess = createVerifiedBitmap(qrBitmap.getWidth(), qrBitmap.getHeight(), R.drawable.ic_close_white_48dp);

      qrVerified.setImageBitmap(qrSuccess);
      qrVerified.getBackground().setColorFilter(getResources().getColor(R.color.red_500), PorterDuff.Mode.MULTIPLY);

      animateVerified();
    }

    private void animateVerified() {
      ScaleAnimation scaleAnimation = new ScaleAnimation(0, 1, 0, 1,
                                                         ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                                                         ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
      scaleAnimation.setInterpolator(new OvershootInterpolator());
      scaleAnimation.setDuration(800);
      scaleAnimation.setAnimationListener(new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {}

        @Override
        public void onAnimationEnd(Animation animation) {
          qrVerified.postDelayed(new Runnable() {
            @Override
            public void run() {
              ScaleAnimation scaleAnimation = new ScaleAnimation(1, 0, 1, 0,
                                                                 ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                                                                 ScaleAnimation.RELATIVE_TO_SELF, 0.5f);

              scaleAnimation.setInterpolator(new AnticipateInterpolator());
              scaleAnimation.setDuration(500);
              ViewUtil.animateOut(qrVerified, scaleAnimation, View.GONE);
            }
          }, 2000);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {}
      });

      ViewUtil.animateIn(qrVerified, scaleAnimation);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
      new AsyncTask<Recipient, Void, Void>() {
        @Override
        protected Void doInBackground(Recipient... params) {
          synchronized (SESSION_LOCK) {
            if (isChecked) {
              Log.w(TAG, "Saving identity: " + params[0].getAddress());
              DatabaseFactory.getIdentityDatabase(getActivity())
                             .saveIdentity(params[0].getAddress(),
                                           remoteIdentity,
                                           IdentityDatabase.VerifiedStatus.VERIFIED, false,
                                           System.currentTimeMillis(), true);
            } else {
              DatabaseFactory.getIdentityDatabase(getActivity())
                             .setVerified(params[0].getAddress(),
                                          remoteIdentity,
                                          IdentityDatabase.VerifiedStatus.DEFAULT);
            }

            ApplicationContext.getInstance(getActivity())
                              .getJobManager()
                              .add(new MultiDeviceVerifiedUpdateJob(getActivity(),
                                                                    recipient.getAddress(),
                                                                    remoteIdentity,
                                                                    isChecked ? IdentityDatabase.VerifiedStatus.VERIFIED :
                                                                                IdentityDatabase.VerifiedStatus.DEFAULT));

            IdentityUtil.markIdentityVerified(getActivity(), new MasterSecretUnion(masterSecret), recipient, isChecked, false);
          }
          return null;
        }
      }.execute(recipient);
    }
  }









  public static class VerifyScanFragment extends Fragment {

    public static final String REMOTE_ADDRESS  = "remote_address";
    public static final String REMOTE_NUMBER   = "remote_number";
    public static final String REMOTE_IDENTITY = "remote_identity";
    public static final String LOCAL_IDENTITY  = "local_identity";
    public static final String LOCAL_NUMBER    = "local_number";
    public static final String VERIFIED_STATE  = "verified_state";
    private boolean    animateSuccessOnDraw = false;
    private boolean    animateFailureOnDraw = false;

    private MasterSecret masterSecret;
    private Recipient    recipient;
    private String       localNumber;
    private String       remoteNumber;

    private IdentityKey localIdentity;
    private IdentityKey remoteIdentity;

    private Fingerprint fingerprint;

    private ImageView     qrVerifiedfrag;

    private View           container;
    private CameraView     cameraView;
    private ScanningThread scanningThread;
    private ScanListener   scanListener;
    private ImageView qrcodefrag;

    public void setScannedFingerprint(String scanned) {
      try {


        if (this.fingerprint.getScannableFingerprint().compareTo(scanned.getBytes("ISO-8859-1"))) {

          this.animateSuccessOnDraw = true;

          // Elham edit starts
          // If the Qr code scanning was successful

          Log.w(TAG, "Saving identity QR code scan data: " + this.recipient.getAddress());
          DatabaseFactory.getIdentityDatabase(getActivity())
                  .saveIdentity(this.recipient.getAddress(),
                          remoteIdentity,
                          IdentityDatabase.VerifiedStatus.VERIFIED, false,
                          System.currentTimeMillis(), true);
          // Should make animation
         // this.verified.setOnCheckedChangeListener(null);
          //this.verified.setChecked(true);
          //this.verified.setOnCheckedChangeListener(this);
            animateVerifiedSuccess();

          // Elham edit Ends


          DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                  //Retry button clicked
                  getActivity().onBackPressed();
                  break;


                case DialogInterface.BUTTON_NEGATIVE:
                  //Cancel button clicked

                  // getSupportFragmentManager().popBackStack();

                  break;


              }
            }
          };

          AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
          builder.setMessage("Your conversation is secure now.").setPositiveButton("OK", dialogClickListener).setTitle("Congratulations!").show();

        } else {

          this.animateFailureOnDraw = true;

          // Elham edit starts
          // If the Qr code scaning was not successful

          Log.w(TAG, "Remove identity: " + this.recipient.getAddress());
          DatabaseFactory.getIdentityDatabase(getActivity())
                  .setVerified(this.recipient.getAddress(),
                          remoteIdentity,
                          IdentityDatabase.VerifiedStatus.DEFAULT);

           animateVerifiedFailure();


          DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                  //Retry button clicked
                  onResume();
                  break;


                case DialogInterface.BUTTON_NEGATIVE:
                  //Cancel button clicked

                 // getSupportFragmentManager().popBackStack();
                  getActivity().onBackPressed();
                   break;


              }
            }
          };

          AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
          builder.setMessage("This doesn't look like your safety number with "+recipient.getName()+". Are you verifying the correct contact?").setPositiveButton("Retry", dialogClickListener)
                  .setNegativeButton("Cancel", dialogClickListener).setTitle("Failed to verify Safety Number!").show();
          // Should make animation
          //this.verified.setOnCheckedChangeListener(null);
          //this.verified.setChecked(false);
          //this.verified.setOnCheckedChangeListener(this);

          // Elham edit Ends
        }
      } catch (FingerprintVersionMismatchException e) {
        Log.w(TAG, e);
        if (e.getOurVersion() < e.getTheirVersion()) {
          Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_your_contact_is_running_a_newer_version_of_Signal, Toast.LENGTH_LONG).show();
        } else {
          Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_your_contact_is_running_an_old_version_of_signal, Toast.LENGTH_LONG).show();
        }
      } catch (FingerprintParsingException e) {
        Log.w(TAG, e);
        Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_the_scanned_qr_code_is_not_a_correctly_formatted_safety_number, Toast.LENGTH_LONG).show();
      } catch (UnsupportedEncodingException e) {
        throw new AssertionError(e);
      }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
      this.container  = ViewUtil.inflate(inflater, viewGroup, R.layout.verify_scan_fragment);
      this.cameraView = ViewUtil.findById(container, R.id.scanner);
      this.qrcodefrag = ViewUtil.findById(container,R.id.qr_code_frag);
      this.qrVerifiedfrag       = ViewUtil.findById(container, R.id.qr_verified_frag);
      Address               address                  = getArguments().getParcelable(REMOTE_ADDRESS);
      IdentityKeyParcelable localIdentityParcelable  = getArguments().getParcelable(LOCAL_IDENTITY);
      IdentityKeyParcelable remoteIdentityParcelable = getArguments().getParcelable(REMOTE_IDENTITY);

      if (address == null)                  throw new AssertionError("Address required");
      if (localIdentityParcelable == null)  throw new AssertionError("local identity required");
      if (remoteIdentityParcelable == null) throw new AssertionError("remote identity required");

      this.masterSecret   = getArguments().getParcelable("master_secret");
      this.localNumber    = getArguments().getString(LOCAL_NUMBER);
      this.localIdentity  = localIdentityParcelable.get();
      this.remoteNumber   = getArguments().getString(REMOTE_NUMBER);
      this.recipient      = RecipientFactory.getRecipientFor(getActivity(), address, true);
      this.remoteIdentity = remoteIdentityParcelable.get();

      new AsyncTask<Void, Void, Fingerprint>() {
        @Override
        protected Fingerprint doInBackground(Void... params) {
          return new NumericFingerprintGenerator(5200).createFor(localNumber, localIdentity,
                  remoteNumber, remoteIdentity);
        }

        @Override
        protected void onPostExecute(Fingerprint fingerprint) {
          VerifyScanFragment.this.fingerprint = fingerprint;
          byte[] qrCodeData   = fingerprint.getScannableFingerprint().getSerialized();
          String qrCodeString = new String(qrCodeData, Charset.forName("ISO-8859-1"));
          Bitmap qrCodeBitmap = QrCode.create(qrCodeString);

          VerifyScanFragment.this.qrcodefrag.setImageBitmap(qrCodeBitmap);

        }
      }.execute();


     //  this.qrcodefrag.setImageResource(R.drawable.warning1);

      return container;

    }
    private Bitmap createVerifiedBitmap(int width, int height, @DrawableRes int id) {
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      Bitmap check  = BitmapFactory.decodeResource(getResources(), id);
      float  offset = (width - check.getWidth()) / 2;

      canvas.drawBitmap(check, offset, offset, null);

      return bitmap;
    }
    private void animateVerifiedFailure() {
      Bitmap qrBitmap  = ((BitmapDrawable)qrcodefrag.getDrawable()).getBitmap();
      Bitmap qrSuccess = createVerifiedBitmap(qrBitmap.getWidth(), qrBitmap.getHeight(), R.drawable.ic_close_white_48dp);

      qrVerifiedfrag.setImageBitmap(qrSuccess);
      qrVerifiedfrag.getBackground().setColorFilter(getResources().getColor(R.color.red_500), PorterDuff.Mode.MULTIPLY);

      animateVerified();
    }
    private void animateVerifiedSuccess() {
      Bitmap qrBitmap  = ((BitmapDrawable)qrcodefrag.getDrawable()).getBitmap();
      Bitmap qrSuccess = createVerifiedBitmap(qrBitmap.getWidth(), qrBitmap.getHeight(), R.drawable.ic_check_white_48dp);

      qrVerifiedfrag.setImageBitmap(qrSuccess);
      qrVerifiedfrag.getBackground().setColorFilter(getResources().getColor(R.color.green_500), PorterDuff.Mode.MULTIPLY);

      animateVerified();
    }

    private void animateVerified() {
      ScaleAnimation scaleAnimation = new ScaleAnimation(0, 1, 0, 1,
              ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
              ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
      scaleAnimation.setInterpolator(new OvershootInterpolator());
      scaleAnimation.setDuration(800);
      scaleAnimation.setAnimationListener(new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {}

        @Override
        public void onAnimationEnd(Animation animation) {
          qrVerifiedfrag.postDelayed(new Runnable() {
            @Override
            public void run() {
              ScaleAnimation scaleAnimation = new ScaleAnimation(1, 0, 1, 0,
                      ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                      ScaleAnimation.RELATIVE_TO_SELF, 0.5f);

              scaleAnimation.setInterpolator(new AnticipateInterpolator());
              scaleAnimation.setDuration(500);
              ViewUtil.animateOut(qrVerifiedfrag, scaleAnimation, View.GONE);
            }
          }, 2000);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {}
      });

      ViewUtil.animateIn(qrVerifiedfrag, scaleAnimation);
    }


    @Override
    public void onResume() {
      super.onResume();
      this.scanningThread = new ScanningThread();
      this.scanningThread.setScanListener(scanListener);
      this.scanningThread.setCharacterSet("ISO-8859-1");
      this.cameraView.onResume();
      this.cameraView.setPreviewCallback(scanningThread);
      this.scanningThread.start();
      if (animateSuccessOnDraw) {
        animateSuccessOnDraw = false;
      //  animateVerifiedSuccess();
      } else if (animateFailureOnDraw) {
       // animateFailureOnDraw = false;
        animateVerifiedFailure();
      }
    }

    @Override
    public void onPause() {
      super.onPause();
      this.cameraView.onPause();
      this.scanningThread.stopScanning();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfiguration) {
      super.onConfigurationChanged(newConfiguration);
      this.cameraView.onPause();
      this.cameraView.onResume();
      this.cameraView.setPreviewCallback(scanningThread);
    }

    public void setScanListener(ScanListener listener) {
      if (this.scanningThread != null) scanningThread.setScanListener(listener);
      this.scanListener = listener;
    }

  }

}
