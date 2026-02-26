package me.ghui.v2er.module.home;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import me.ghui.v2er.R;
import me.ghui.v2er.general.Pref;
import me.ghui.v2er.network.APIService;
import me.ghui.v2er.network.GeneralConsumer;
import me.ghui.v2er.network.bean.DailyInfo;
import me.ghui.v2er.util.Check;
import me.ghui.v2er.util.UserUtils;
import me.ghui.v2er.util.Utils;

import static me.ghui.v2er.widget.FollowProgressBtn.FINISHED;
import static me.ghui.v2er.widget.FollowProgressBtn.NORMAL;

/**
 * Created by ghui on 07/08/2017.
 */

public class CheckInPresenter implements CheckInContract.IPresenter {
    private static final int MAX_RETRY_COUNT = 3;
    private static final long INITIAL_DELAY_MS = 500;

    private CheckInContract.IView mView;
    private String checkInDaysStr;
    private int retryCount = 0;

    public CheckInPresenter(CheckInContract.IView view) {
        mView = view;
    }

    @Override
    public void start() {
        checkIn(Pref.readBool(R.string.pref_key_auto_checkin));
    }

    @Override
    public void checkIn(boolean needAutoCheckIn) {
        if (!UserUtils.isLogin()) return;
        retryCount = 0; // Reset retry count
        mView.checkInBtn().startUpdate();
        APIService.get().dailyInfo()
                .compose(mView.rx(null))
                .subscribe(new GeneralConsumer<DailyInfo>(mView) {
                    @Override
                    public void onConsume(DailyInfo checkInInfo) {
                        if (checkInInfo.hadCheckedIn()) {
                            checkInDaysStr = checkInInfo.getCheckinDays();
                            mView.checkInBtn().setStatus(FINISHED, "已签到/" + checkInDaysStr + "天", R.drawable.progress_button_done_icon);
                        } else {
                            if (needAutoCheckIn) {
                                String once = checkInInfo.once();
                                if (Check.isEmpty(once)) {
                                    mView.toast("签到失败: 无法获取签到令牌");
                                    mView.checkInBtn().setStatus(NORMAL, "签到", R.drawable.progress_button_checkin_icon);
                                } else {
                                    // Add a small delay before check-in to avoid rate limiting
                                    checkInWithDelay(once, INITIAL_DELAY_MS);
                                }
                            } else {
                                mView.checkInBtn().setStatus(NORMAL, "签到", R.drawable.progress_button_checkin_icon);
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        super.onError(e);
                        mView.checkInBtn().setStatus(NORMAL, "签到", R.drawable.progress_button_checkin_icon);
                    }
                });
    }

    @Override
    public int checkInDays() {
        return Utils.getIntFromString(checkInDaysStr);
    }


    private void checkInWithDelay(String once, long delayMs) {
        Observable.timer(delayMs, TimeUnit.MILLISECONDS)
                .compose(mView.rx(null))
                .subscribe(ignored -> checkIn(once), e -> {
                    checkIn(once);
                });
    }

    private void checkIn(String once) {
        mView.checkInBtn().startUpdate();
        APIService.get()
                .checkIn(once)
                .compose(mView.rx(null))
                .subscribe(new GeneralConsumer<DailyInfo>(mView) {
                    @Override
                    public void onConsume(DailyInfo checkInInfo) {
                        if (checkInInfo.hadCheckedIn()) {
                            retryCount = 0;
                            checkInDaysStr = checkInInfo.getCheckinDays();
                            mView.toast("签到成功/" + checkInDaysStr + "天");
                            mView.checkInBtn().setStatus(FINISHED, "已签到/" + checkInDaysStr + "天", R.drawable.progress_button_done_icon);
                        } else {
                            // Server returned success but not checked in — retry with backoff
                            if (retryCount < MAX_RETRY_COUNT) {
                                retryCount++;
                                long delay = INITIAL_DELAY_MS * (1L << retryCount); // 1s, 2s, 4s
                                checkInWithDelay(once, delay);
                            } else {
                                mView.toast("签到失败，请手动签到");
                                mView.checkInBtn().setStatus(NORMAL, "签到", R.drawable.progress_button_checkin_icon);
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        super.onError(e);
                        if (retryCount < MAX_RETRY_COUNT) {
                            retryCount++;
                            long delay = INITIAL_DELAY_MS * (1L << retryCount);
                            checkInWithDelay(once, delay);
                        } else {
                            mView.toast("签到失败，请手动签到");
                            mView.checkInBtn().setStatus(NORMAL, "签到", R.drawable.progress_button_checkin_icon);
                        }
                    }
                });
    }
}

