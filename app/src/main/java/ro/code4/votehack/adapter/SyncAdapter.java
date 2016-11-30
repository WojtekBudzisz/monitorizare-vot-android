package ro.code4.votehack.adapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ro.code4.votehack.constants.Sync;
import ro.code4.votehack.db.Data;
import ro.code4.votehack.net.NetworkService;
import ro.code4.votehack.net.model.Form;
import ro.code4.votehack.net.model.Question;
import ro.code4.votehack.net.model.QuestionAnswer;
import ro.code4.votehack.net.model.ResponseAnswerContainer;
import ro.code4.votehack.net.model.Version;
import ro.code4.votehack.net.model.response.VersionResponse;
import ro.code4.votehack.util.Logify;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        init(context);
    }

    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        init(context);
    }

    private void init(Context context) {

    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        Logify.d("SyncAdapter", "performing sync");

        try {
            postQuestionAnswers();
            getFormsDefinition();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void postQuestionAnswers() throws IOException {
        List<QuestionAnswer> questionAnswers = new ArrayList<>();
        getAnswersFromSectionA(Data.getInstance().getFormA(), questionAnswers);
        getAnswersFromSectionA(Data.getInstance().getFormB(), questionAnswers);
        getAnswersFromSectionA(Data.getInstance().getFormC(), questionAnswers);
        NetworkService.postQuestionAnswer(new ResponseAnswerContainer(questionAnswers));
    }

    private void getAnswersFromSectionA(Form form, List<QuestionAnswer> questionAnswers) {
        if(form != null){
            List<Question> questionList = form.getQuestionList();
            for (Question question : questionList) {
                if(!question.isSynced() && question.getRaspunsuriIntrebare().size() > 0){
                    QuestionAnswer questionAnswer = new QuestionAnswer(question.getId(),
                            "AB", 1, form.getSectionCode(), question.getRaspunsuriIntrebare());
                    questionAnswers.add(questionAnswer);
                }
            }
        }
    }

    private void getFormsDefinition() throws IOException {
        VersionResponse versionResponse = NetworkService.doGetFormVersion();
        Version existingVersion = Data.getInstance().getFormVersion();
        if(!versionsEqual(existingVersion, versionResponse.getVersion())) {
            Data.getInstance().saveFormsVersion(versionResponse.getVersion());
            // TODO clear any saved question when definition changes
            getForms();
        }
    }

    private boolean versionsEqual(Version before, Version current) {
        return (before != null)
                && before.getA().equals(current.getA())
                && before.getB().equals(current.getB())
                && before.getC().equals(current.getC());
    }

    private void getForms() throws IOException {
        NetworkService.doGetForm("A");
        NetworkService.doGetForm("B");
        NetworkService.doGetForm("C");
    }

    public static void requestSync(Context context) {
        Bundle settingsBundle = new Bundle();
        settingsBundle.putBoolean(
                ContentResolver.SYNC_EXTRAS_MANUAL, true);
        settingsBundle.putBoolean(
                ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        ContentResolver.requestSync(createSyncAccount(context), Sync.AUTHORITY, settingsBundle);
    }

    private static Account createSyncAccount(Context context) {
        Account newAccount = new Account(Sync.ACCOUNT, Sync.ACCOUNT_TYPE);
        AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
        if (accountManager.addAccountExplicitly(newAccount, null, null)) {
            return newAccount;
        }
        try {
            return accountManager.getAccountsByType(Sync.ACCOUNT_TYPE)[0];
        } catch (SecurityException e) {
            Toast.makeText(context, "Eroare permisiune " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
    }
}