package info.blockchain.wallet.ui;

import com.google.common.collect.HashBiMap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.android.Contents;
import com.google.zxing.client.android.encode.QRCodeEncoder;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TextView;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import info.blockchain.wallet.callbacks.CustomKeypadCallback;
import info.blockchain.wallet.payload.Account;
import info.blockchain.wallet.payload.HDPayloadBridge;
import info.blockchain.wallet.payload.LegacyAddress;
import info.blockchain.wallet.payload.PayloadFactory;
import info.blockchain.wallet.payload.ReceiveAddress;
import info.blockchain.wallet.ui.helpers.CustomKeypad;
import info.blockchain.wallet.ui.helpers.ToastCustom;
import info.blockchain.wallet.util.AppUtil;
import info.blockchain.wallet.util.ExchangeRateFactory;
import info.blockchain.wallet.util.MonetaryUtil;
import info.blockchain.wallet.util.PrefsUtil;

import org.apache.commons.codec.DecoderException;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.uri.BitcoinURI;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import piuk.blockchain.android.R;

public class ReceiveFragment extends Fragment implements CustomKeypadCallback {

    private Locale locale = null;

    private ImageView ivReceivingQR = null;
    private TextView edReceivingAddress = null;
    private ImageView addressInfo = null;

    private EditText edAmount1 = null;
    private EditText edAmount2 = null;
    private Spinner spAccounts = null;
    private TextView tvCurrency1 = null;
    private TextView tvFiat2 = null;
    public static CustomKeypad customKeypad;

    private SlidingUpPanelLayout mLayout;
    private ListView sendPaymentCodeAppListlist;
    private View rootView;
    private LinearLayout mainContentShadow;

    //Drop down
    private ArrayAdapter<String> receiveToAdapter = null;
    private List<String> receiveToList = null;
    private HashBiMap<Object, Integer> accountBiMap = null;
    private HashMap<Integer, Integer> spinnerIndexAccountIndexMap = null;

    //text
    private boolean textChangeAllowed = true;
    private String defaultSeperator;
    private String strBTC = "BTC";
    private String strFiat = null;
    private boolean isBTC = true;
    private double btc_fx = 319.13;
    private final String addressInfoLink = "https://support.blockchain.com/hc/en-us/articles/210353663-Why-is-my-bitcoin-address-changing-";
    private PrefsUtil prefs;

    protected BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {

            if (BalanceFragment.ACTION_INTENT.equals(intent.getAction())) {
                updateSpinnerList();
                displayQRCode(spAccounts.getSelectedItemPosition());
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_receive, container, false);
        this.rootView = rootView;

        prefs = new PrefsUtil(getActivity());

        locale = Locale.getDefault();

        setupToolbar();

        defaultSeperator = getDefaultDecimalSeparator();

        setupViews();

        setCustomKeypad();

        return rootView;
    }

    private void setupToolbar(){

        Toolbar toolbar = (Toolbar) getActivity().findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(getResources().getDrawable(R.drawable.ic_arrow_back_white_24dp));
        toolbar.setNavigationOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                View view = getActivity().getCurrentFocus();
                if (view != null) {
                    InputMethodManager inputManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
                Fragment fragment = new BalanceFragment();
                FragmentManager fragmentManager = getFragmentManager();
                fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();
            }
        });

        if(((AppCompatActivity) getActivity()).getSupportActionBar() == null){
            ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
        }

        ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayShowTitleEnabled(true);
        ((AppCompatActivity) getActivity()).findViewById(R.id.account_spinner).setVisibility(View.GONE);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.receive_bitcoin);
        setHasOptionsMenu(true);
    }

    private String getDefaultDecimalSeparator(){
        DecimalFormat format = (DecimalFormat) DecimalFormat.getInstance(Locale.getDefault());
        DecimalFormatSymbols symbols = format.getDecimalFormatSymbols();
        return Character.toString(symbols.getDecimalSeparator());
    }

    private void setupViews(){

        mainContentShadow = (LinearLayout) rootView.findViewById(R.id.receive_main_content_shadow);
        mainContentShadow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLayout.getPanelState().equals(SlidingUpPanelLayout.PanelState.COLLAPSED)) {
                    onShareClicked();
                }
            }
        });

        ivReceivingQR = (ImageView) rootView.findViewById(R.id.qr);
        ivReceivingQR.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.receive_address_to_clipboard)
                        .setCancelable(false)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getActivity().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = null;
                                clip = android.content.ClipData.newPlainText("Send address", edReceivingAddress.getText().toString());
                                ToastCustom.makeText(getActivity(), getString(R.string.copied_to_clipboard), ToastCustom.LENGTH_LONG, ToastCustom.TYPE_GENERAL);
                                clipboard.setPrimaryClip(clip);

                            }

                        }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int whichButton) {
                        ;
                    }
                }).show();

            }
        });

        ivReceivingQR.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View view) {

                onShareClicked();

                return true;
            }
        });

        tvCurrency1 = (TextView) rootView.findViewById(R.id.currency1);
        tvFiat2 = (TextView) rootView.findViewById(R.id.fiat2);

        edAmount1 = (EditText) rootView.findViewById(R.id.amount1);
        edAmount1.setKeyListener(DigitsKeyListener.getInstance("0123456789" + defaultSeperator));
        edAmount1.setHint("0" + defaultSeperator + "00");
        edAmount1.setSelectAllOnFocus(true);
        edAmount1.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {

                String input = s.toString();

                edAmount1.removeTextChangedListener(this);

                int unit = prefs.getValue(PrefsUtil.KEY_BTC_UNITS, MonetaryUtil.UNIT_BTC);
                int max_len = 8;
                NumberFormat btcFormat = NumberFormat.getInstance(Locale.getDefault());
                switch (unit) {
                    case MonetaryUtil.MICRO_BTC:
                        max_len = 2;
                        break;
                    case MonetaryUtil.MILLI_BTC:
                        max_len = 4;
                        break;
                    default:
                        max_len = 8;
                        break;
                }
                btcFormat.setMaximumFractionDigits(max_len + 1);
                btcFormat.setMinimumFractionDigits(0);

                try {
                    if (input.indexOf(defaultSeperator) != -1) {
                        String dec = input.substring(input.indexOf(defaultSeperator));
                        if (dec.length() > 0) {
                            dec = dec.substring(1);
                            if (dec.length() > max_len) {
                                edAmount1.setText(input.substring(0, input.length() - 1));
                                edAmount1.setSelection(edAmount1.getText().length());
                                s = edAmount1.getEditableText();
                            }
                        }
                    }
                } catch (NumberFormatException nfe) {
                    ;
                }

                edAmount1.addTextChangedListener(this);

                if (textChangeAllowed) {
                    textChangeAllowed = false;
                    updateFiatTextField(s.toString());

                    displayQRCode(spAccounts.getSelectedItemPosition());
                    textChangeAllowed = true;
                }

                if (s.toString().contains(defaultSeperator))
                    edAmount1.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
                else
                    edAmount1.setKeyListener(DigitsKeyListener.getInstance("0123456789" + defaultSeperator));
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                ;
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                ;
            }
        });

        edAmount2 = (EditText) rootView.findViewById(R.id.amount2);
        edAmount2.setKeyListener(DigitsKeyListener.getInstance("0123456789" + defaultSeperator));
        edAmount2.setHint("0" + defaultSeperator + "00");
        edAmount2.setSelectAllOnFocus(true);
        edAmount2.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {

                String input = s.toString();

                edAmount2.removeTextChangedListener(this);

                int max_len = 2;
                NumberFormat fiatFormat = NumberFormat.getInstance(Locale.getDefault());
                fiatFormat.setMaximumFractionDigits(max_len + 1);
                fiatFormat.setMinimumFractionDigits(0);

                try {
                    if (input.indexOf(defaultSeperator) != -1) {
                        String dec = input.substring(input.indexOf(defaultSeperator));
                        if (dec.length() > 0) {
                            dec = dec.substring(1);
                            if (dec.length() > max_len) {
                                edAmount2.setText(input.substring(0, input.length() - 1));
                                edAmount2.setSelection(edAmount2.getText().length());
                                s = edAmount2.getEditableText();
                            }
                        }
                    }
                } catch (NumberFormatException nfe) {
                    ;
                }

                edAmount2.addTextChangedListener(this);

                if (textChangeAllowed) {
                    textChangeAllowed = false;
                    updateBtcTextField(s.toString());

                    displayQRCode(spAccounts.getSelectedItemPosition());
                    textChangeAllowed = true;
                }

                if (s.toString().contains(defaultSeperator))
                    edAmount2.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
                else
                    edAmount2.setKeyListener(DigitsKeyListener.getInstance("0123456789" + defaultSeperator));
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                ;
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                ;
            }
        });

        spAccounts = (Spinner) rootView.findViewById(R.id.accounts);
        receiveToList = new ArrayList<>();
        accountBiMap = HashBiMap.create();
        spinnerIndexAccountIndexMap = new HashMap<>();
        updateSpinnerList();

        if (receiveToList.size() == 1)
            rootView.findViewById(R.id.from_row).setVisibility(View.GONE);

        receiveToAdapter = new ArrayAdapter<String>(getActivity(), R.layout.spinner_item, receiveToList);
        receiveToAdapter.setDropDownViewResource(R.layout.spinner_dropdown);
        spAccounts.setAdapter(receiveToAdapter);
        spAccounts.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    spAccounts.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    spAccounts.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    spAccounts.setDropDownWidth(spAccounts.getWidth());
                }
            }
        });
        spAccounts.post(new Runnable() {
            public void run() {
                spAccounts.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {

                        spAccounts.setSelection(spAccounts.getSelectedItemPosition());
                        Object object = accountBiMap.inverse().get(spAccounts.getSelectedItemPosition());

                        if(prefs.getValue("WARN_WATCH_ONLY_SPEND", true)){
                            promptWatchOnlySpendWarning(object);
                        }

                        displayQRCode(spAccounts.getSelectedItemPosition());
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> arg0) {
                        ;
                    }
                });
            }
        });

        strBTC = MonetaryUtil.getInstance().getBTCUnit(prefs.getValue(PrefsUtil.KEY_BTC_UNITS, MonetaryUtil.UNIT_BTC));
        strFiat = prefs.getValue(PrefsUtil.KEY_SELECTED_FIAT, PrefsUtil.DEFAULT_CURRENCY);
        btc_fx = ExchangeRateFactory.getInstance(getActivity()).getLastPrice(strFiat);

        tvCurrency1.setText(strBTC);
        tvFiat2.setText(strFiat);

        edReceivingAddress = (TextView) rootView.findViewById(R.id.receiving_address);

        mLayout = (SlidingUpPanelLayout) rootView.findViewById(R.id.sliding_layout);
        mLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
        mLayout.setTouchEnabled(false);
        mLayout.setPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {
            }

            @Override
            public void onPanelExpanded(View panel) {

            }

            @Override
            public void onPanelCollapsed(View panel) {

            }

            @Override
            public void onPanelAnchored(View panel) {
            }

            @Override
            public void onPanelHidden(View panel) {
            }
        });

        addressInfo = (ImageView)rootView.findViewById(R.id.iv_address_info);
        addressInfo.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                new AlertDialog.Builder(getActivity())
                        .setTitle(getString(R.string.why_has_my_address_changed))
                        .setMessage(getString(R.string.new_address_info))
                        .setPositiveButton(R.string.learn_more, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent();
                                intent.setData(Uri.parse(addressInfoLink));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton(R.string.ok, null)
                        .show();
            }
        });
    }

    private void setCustomKeypad(){

        customKeypad = new CustomKeypad(getActivity(), ((TableLayout) rootView.findViewById(R.id.numericPad)));
        customKeypad.setDecimalSeparator(defaultSeperator);

        //Enable custom keypad and disables default keyboard from popping up
        customKeypad.enableOnView(edAmount1);
        customKeypad.enableOnView(edAmount2);

        edAmount1.setText("");
        edAmount1.requestFocus();
    }

    private void promptWatchOnlySpendWarning(Object object){

        if (object instanceof LegacyAddress && ((LegacyAddress) object).isWatchOnly()) {

            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());
            LayoutInflater inflater = getActivity().getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.alert_watch_only_spend, null);
            dialogBuilder.setView(dialogView);
            dialogBuilder.setCancelable(false);

            final AlertDialog alertDialog = dialogBuilder.create();
            alertDialog.setCanceledOnTouchOutside(false);

            final CheckBox confirmDismissForever = (CheckBox) dialogView.findViewById(R.id.confirm_dont_ask_again);

            TextView confirmCancel = (TextView) dialogView.findViewById(R.id.confirm_cancel);
            confirmCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    spAccounts.setSelection(0, true);
                    if(confirmDismissForever.isChecked())prefs.setValue("WARN_WATCH_ONLY_SPEND", false);
                    alertDialog.dismiss();
                }
            });

            TextView confirmContinue = (TextView) dialogView.findViewById(R.id.confirm_continue);
            confirmContinue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(confirmDismissForever.isChecked())prefs.setValue("WARN_WATCH_ONLY_SPEND", false);
                    alertDialog.dismiss();
                }
            });

            alertDialog.show();
        }
    }

    private void updateSpinnerList() {
        //receiveToList is linked to Adapter - do not reconstruct or loose reference otherwise notifyDataSetChanged won't work
        receiveToList.clear();
        accountBiMap.clear();
        spinnerIndexAccountIndexMap.clear();

        int spinnerIndex = 0;

        if (PayloadFactory.getInstance().get().isUpgraded()) {

            //V3
            List<Account> accounts = PayloadFactory.getInstance().get().getHdWallet().getAccounts();
            int accountIndex = 0;
            for (Account item : accounts) {

                spinnerIndexAccountIndexMap.put(spinnerIndex, accountIndex);
                accountIndex++;

                if (item.isArchived())
                    continue;//skip archived account

                //no xpub watch only yet

                receiveToList.add(item.getLabel());
                accountBiMap.put(item, spinnerIndex);
                spinnerIndex++;
            }
        }

        List<LegacyAddress> legacyAddresses = PayloadFactory.getInstance().get().getLegacyAddresses();

        for (LegacyAddress legacyAddress : legacyAddresses) {

            if (legacyAddress.getTag() == PayloadFactory.ARCHIVED_ADDRESS)
                continue;//skip archived address

            //If address has no label, we'll display address
            String labelOrAddress = legacyAddress.getLabel() == null || legacyAddress.getLabel().length() == 0 ? legacyAddress.getAddress() : legacyAddress.getLabel();

            //Prefix "watch-only"
            if (legacyAddress.isWatchOnly()) {
                labelOrAddress = getActivity().getString(R.string.watch_only_label) + " " + labelOrAddress;
            }

            receiveToList.add(labelOrAddress);
            accountBiMap.put(legacyAddress, spinnerIndex);
            spinnerIndex++;
        }

        //Notify adapter of list update
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (receiveToAdapter != null) receiveToAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser) {
            strBTC = MonetaryUtil.getInstance().getBTCUnit(prefs.getValue(PrefsUtil.KEY_BTC_UNITS, MonetaryUtil.UNIT_BTC));
            strFiat = prefs.getValue(PrefsUtil.KEY_SELECTED_FIAT, PrefsUtil.DEFAULT_CURRENCY);
            btc_fx = ExchangeRateFactory.getInstance(getActivity()).getLastPrice(strFiat);
            tvCurrency1.setText(isBTC ? strBTC : strFiat);
            tvFiat2.setText(isBTC ? strFiat : strBTC);
        } else {
            ;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        MainActivity.currentFragment = this;

        strBTC = MonetaryUtil.getInstance().getBTCUnit(prefs.getValue(PrefsUtil.KEY_BTC_UNITS, MonetaryUtil.UNIT_BTC));
        strFiat = prefs.getValue(PrefsUtil.KEY_SELECTED_FIAT, PrefsUtil.DEFAULT_CURRENCY);
        btc_fx = ExchangeRateFactory.getInstance(getActivity()).getLastPrice(strFiat);
        tvCurrency1.setText(isBTC ? strBTC : strFiat);
        tvFiat2.setText(isBTC ? strFiat : strBTC);

        selectDefaultAccount();

        updateSpinnerList();

        IntentFilter filter = new IntentFilter(BalanceFragment.ACTION_INTENT);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(receiver, filter);
    }

    private void selectDefaultAccount() {

        if (spAccounts != null) {

            if (PayloadFactory.getInstance().get().isUpgraded()) {
                int defaultIndex = PayloadFactory.getInstance().get().getHdWallet().getDefaultIndex();
                Account defaultAccount = PayloadFactory.getInstance().get().getHdWallet().getAccounts().get(defaultIndex);
                int defaultSpinnerIndex = accountBiMap.get(defaultAccount);
                displayQRCode(defaultSpinnerIndex);
            } else {
                //V2
                displayQRCode(0);//default to 0
            }
        }
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(receiver);
        super.onPause();
    }

    private void displayQRCode(int spinnerIndex) {

        spAccounts.setSelection(spinnerIndex);
        String receiveAddress = null;

        Object object = accountBiMap.inverse().get(spinnerIndex);

        if (object instanceof LegacyAddress) {

            //V2
            receiveAddress = ((LegacyAddress) object).getAddress();

        } else {
            //V3
            receiveAddress = getV3ReceiveAddress((Account) object);
        }

        edReceivingAddress.setText(receiveAddress);

        BigInteger bamount = null;
        try {
            long lamount = 0L;
            if (isBTC) {
                lamount = (long) (Math.round(NumberFormat.getInstance(locale).parse(edAmount1.getText().toString()).doubleValue() * 1e8));
            } else {
                lamount = (long) (Math.round(NumberFormat.getInstance(locale).parse(edAmount2.getText().toString()).doubleValue() * 1e8));
            }
            bamount = MonetaryUtil.getInstance(getActivity()).getUndenominatedAmount(lamount);
            if (bamount.compareTo(BigInteger.valueOf(2100000000000000L)) == 1) {
                ToastCustom.makeText(getActivity(), getActivity().getString(R.string.invalid_amount), ToastCustom.LENGTH_LONG, ToastCustom.TYPE_ERROR);
                return;
            }

            if (!bamount.equals(BigInteger.ZERO)) {
                generateQRCode(BitcoinURI.convertToBitcoinURI(receiveAddress, Coin.valueOf(bamount.longValue()), "", ""));
            } else {
                generateQRCode("bitcoin:" + receiveAddress);
            }
        } catch (NumberFormatException | ParseException e) {
            generateQRCode("bitcoin:" + receiveAddress);
        }
    }

    private void generateQRCode(final String uri) {

        new AsyncTask<Void, Void, Bitmap>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                addressInfo.setVisibility(View.GONE);
                ivReceivingQR.setVisibility(View.GONE);
                edReceivingAddress.setVisibility(View.GONE);
                rootView.findViewById(R.id.progressBar2).setVisibility(View.VISIBLE);
            }

            @Override
            protected Bitmap doInBackground(Void... params) {

                Bitmap bitmap = null;
                int qrCodeDimension = 260;

                QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(uri, null, Contents.Type.TEXT, BarcodeFormat.QR_CODE.toString(), qrCodeDimension);

                try {
                    bitmap = qrCodeEncoder.encodeAsBitmap();
                } catch (WriterException e) {
                    e.printStackTrace();
                }

                return bitmap;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                super.onPostExecute(bitmap);
                rootView.findViewById(R.id.progressBar2).setVisibility(View.GONE);
                ivReceivingQR.setVisibility(View.VISIBLE);
                edReceivingAddress.setVisibility(View.VISIBLE);
                ivReceivingQR.setImageBitmap(bitmap);
                addressInfo.setVisibility(View.VISIBLE);

                setupBottomSheet();
            }
        }.execute();
    }

    private String getV3ReceiveAddress(Account account) {

        try {
            int spinnerIndex = accountBiMap.get(account);
            int accountIndex = spinnerIndexAccountIndexMap.get(spinnerIndex);
            ReceiveAddress receiveAddress = null;
            receiveAddress = new HDPayloadBridge(getActivity()).getReceiveAddress(accountIndex);
            return receiveAddress.getAddress();

        } catch (DecoderException | IOException | MnemonicException.MnemonicWordException | MnemonicException.MnemonicChecksumException | MnemonicException.MnemonicLengthException | AddressFormatException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void updateFiatTextField(String cBtc) {
        if(cBtc.isEmpty())cBtc = "0";
        double btc_amount = 0.0;
        try {
            btc_amount = MonetaryUtil.getInstance(getActivity()).getUndenominatedAmount(NumberFormat.getInstance(locale).parse(cBtc).doubleValue());
        } catch (NumberFormatException | ParseException e) {
            btc_amount = 0.0;
        }
        double fiat_amount = btc_fx * btc_amount;
        edAmount2.setText(MonetaryUtil.getInstance().getFiatFormat(strFiat).format(fiat_amount));
    }

    private void updateBtcTextField(String cfiat) {
        if(cfiat.isEmpty())cfiat = "0";
        double fiat_amount = 0.0;
        try {
            fiat_amount = NumberFormat.getInstance(locale).parse(cfiat).doubleValue();
        } catch (NumberFormatException | ParseException e) {
            fiat_amount = 0.0;
        }
        double btc_amount = fiat_amount / btc_fx;
        edAmount1.setText(MonetaryUtil.getInstance().getBTCFormat().format(MonetaryUtil.getInstance(getActivity()).getDenominatedAmount(btc_amount)));
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        menu.findItem(R.id.action_qr).setVisible(false);
        menu.findItem(R.id.action_send).setVisible(false);
        MenuItem i = menu.findItem(R.id.action_share_receive).setVisible(true);

        i.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {

                InputMethodManager inputManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

                onShareClicked();

                return false;
            }
        });
    }

    private void onShareClicked() {

        customKeypad.setNumpadVisibility(View.GONE);

        if (mLayout != null) {
            if (mLayout.getPanelState() != SlidingUpPanelLayout.PanelState.HIDDEN) {
                mLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
                mainContentShadow.setVisibility(View.GONE);
            } else {

                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.receive_address_to_share)
                        .setCancelable(false)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {

                                mLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
                                mainContentShadow.setVisibility(View.VISIBLE);
                                mainContentShadow.bringToFront();

                            }

                        }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int whichButton) {
                        ;
                    }
                }).show();

            }
        }

    }

    private void setupBottomSheet() {

        //Re-Populate list
        String strFileName = new AppUtil(getActivity()).getReceiveQRFilename();
        File file = new File(strFileName);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (Exception e) {
                ToastCustom.makeText(getActivity(), e.getMessage(), ToastCustom.LENGTH_LONG, ToastCustom.TYPE_ERROR);
            }
        }
        file.setReadable(true, false);

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        }

        if (file != null && fos != null) {
            Bitmap bitmap = ((BitmapDrawable) ivReceivingQR.getDrawable()).getBitmap();
            bitmap.compress(CompressFormat.PNG, 0, fos);

            try {
                fos.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }

            ArrayList<SendPaymentCodeData> dataList = new ArrayList<SendPaymentCodeData>();

            PackageManager pm = getActivity().getPackageManager();

            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            List<ResolveInfo> resInfos = pm.queryIntentActivities(intent, 0);

            SendPaymentCodeData d;
            for (ResolveInfo resInfo : resInfos) {

                String context = resInfo.activityInfo.packageName;
                String packageClassName = resInfo.activityInfo.name;
                CharSequence label = resInfo.loadLabel(pm);
                Drawable icon = resInfo.loadIcon(pm);

                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.setType("image/png");
                shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
                shareIntent.setClassName(context, packageClassName);

                d = new SendPaymentCodeData();
                d.setTitle(label.toString());
                d.setLogo(icon);
                d.setIntent(shareIntent);
                dataList.add(d);
            }

            ArrayAdapter adapter = new SendPaymentCodeAdapter(getActivity(), dataList);
            sendPaymentCodeAppListlist = (ListView) rootView.findViewById(R.id.share_app_list);
            sendPaymentCodeAppListlist.setAdapter(adapter);
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onKeypadClose() {
        customKeypad.setNumpadVisibility(View.GONE);
    }

    class SendPaymentCodeAdapter extends ArrayAdapter<SendPaymentCodeData> {
        private final Context context;
        private final ArrayList<SendPaymentCodeData> repoDataArrayList;

        public SendPaymentCodeAdapter(Context context, ArrayList<SendPaymentCodeData> repoDataArrayList) {

            super(context, R.layout.fragment_receive_share_row, repoDataArrayList);

            this.context = context;
            this.repoDataArrayList = repoDataArrayList;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View rowView = null;
            rowView = inflater.inflate(R.layout.fragment_receive_share_row, parent, false);

            ImageView image = (ImageView) rowView.findViewById(R.id.share_app_image);
            TextView title = (TextView) rowView.findViewById(R.id.share_app_title);

            image.setImageDrawable(repoDataArrayList.get(position).getLogo());
            title.setText(repoDataArrayList.get(position).getTitle());

            rowView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(repoDataArrayList.get(position).getIntent());
                }
            });

            return rowView;
        }
    }

    class SendPaymentCodeData {
        private Drawable logo;
        private String title;
        private Intent intent;

        public SendPaymentCodeData() {

        }

        public Intent getIntent() {
            return intent;
        }

        public void setIntent(Intent intent) {
            this.intent = intent;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Drawable getLogo() {
            return logo;
        }

        public void setLogo(Drawable logo) {
            this.logo = logo;
        }
    }
}
