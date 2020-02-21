package cn.wildfire.chat.kit.voip;

import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import org.webrtc.RendererCommon;
import org.webrtc.StatsReport;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import cn.wildfire.chat.kit.GlideApp;
import cn.wildfire.chat.kit.user.UserViewModel;
import cn.wildfirechat.avenginekit.AVEngineKit;
import cn.wildfirechat.avenginekit.PeerConnectionClient;
import cn.wildfirechat.chat.R;
import cn.wildfirechat.model.UserInfo;

public class MultiCallVideoFragment extends Fragment implements AVEngineKit.CallSessionCallback {
    @BindView(R.id.durationTextView)
    TextView durationTextView;
    @BindView(R.id.participantLinearLayout)
    LinearLayout participantLinearLayout;
    @BindView(R.id.focusVideoContainerFrameLayout)
    FrameLayout focusVideoContainerFrameLayout;
    @BindView(R.id.muteImageView)
    ImageView muteImageView;
    @BindView(R.id.videoImageView)
    ImageView videoImageView;

    private List<String> participants;
    private UserInfo me;
    private UserViewModel userViewModel;

    private String focusVideoUserId;
    private MultiCallItem focusMultiCallItem;

    private RendererCommon.ScalingType scalingType = RendererCommon.ScalingType.SCALE_ASPECT_BALANCED;
    private boolean micEnabled = true;
    private boolean videoEnabled = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.av_multi_video_outgoing_connected, container, false);
        ButterKnife.bind(this, view);
        init();
        return view;
    }

    private AVEngineKit getEngineKit() {
        return AVEngineKit.Instance();
    }

    private void init() {
        userViewModel = ViewModelProviders.of(this).get(UserViewModel.class);
        AVEngineKit.CallSession session = getEngineKit().getCurrentSession();
        if (session == null) {
            getActivity().finish();
            return;
        }

        initParticipantsView(session);
        updateCallDuration();
        updateParticipantStatus(session);
    }

    private void initParticipantsView(AVEngineKit.CallSession session) {
        me = userViewModel.getUserInfo(userViewModel.getUserId(), false);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int with = dm.widthPixels;

        participantLinearLayout.removeAllViews();

        participants = session.getParticipantIds();
        List<UserInfo> participantUserInfos = userViewModel.getUserInfos(participants);
        for (UserInfo userInfo : participantUserInfos) {
            MultiCallItem multiCallItem = new MultiCallItem(getActivity());
            multiCallItem.setTag(userInfo.uid);

            multiCallItem.setLayoutParams(new ViewGroup.LayoutParams(with / 3, with / 3));
            multiCallItem.getStatusTextView().setText(R.string.connecting);
            multiCallItem.setOnClickListener(clickListener);
            GlideApp.with(multiCallItem).load(userInfo.portrait).into(multiCallItem.getPortraitImageView());
            participantLinearLayout.addView(multiCallItem);
        }

        MultiCallItem multiCallItem = new MultiCallItem(getActivity());
        multiCallItem.setTag(me.uid);
        multiCallItem.setLayoutParams(new ViewGroup.LayoutParams(with, with));
        multiCallItem.getStatusTextView().setText(me.displayName);
        GlideApp.with(multiCallItem).load(me.portrait).into(multiCallItem.getPortraitImageView());

        focusVideoContainerFrameLayout.setLayoutParams(new FrameLayout.LayoutParams(with, with));
        focusVideoContainerFrameLayout.addView(multiCallItem);
        focusMultiCallItem = multiCallItem;
        focusVideoUserId = me.uid;
    }

    private void updateParticipantStatus(AVEngineKit.CallSession session) {
        int count = participantLinearLayout.getChildCount();
        String meUid = userViewModel.getUserId();
        for (int i = 0; i < count; i++) {
            View view = participantLinearLayout.getChildAt(i);
            String userId = (String) view.getTag();
            if (meUid.equals(userId)) {
                ((MultiCallItem) view).getStatusTextView().setVisibility(View.GONE);
            } else {
                PeerConnectionClient client = session.getClient(userId);
                if (client.state == AVEngineKit.CallState.Connected) {
                    ((MultiCallItem) view).getStatusTextView().setVisibility(View.GONE);
                }
            }
        }
    }


    @OnClick(R.id.minimizeImageView)
    void minimize() {
        AVEngineKit.CallSession session = getEngineKit().getCurrentSession();
        session.stopVideoSource();
        session.setupLocalVideo(null, scalingType);
        List<String> participants = session.getParticipantIds();
        for (String participant : participants) {
            session.setupRemoteVideo(participant, null, scalingType);
        }
        ((MultiCallActivity) getActivity()).showFloatingView();
    }

    @OnClick(R.id.addParticipantImageView)
    void addParticipant() {
        ((MultiCallActivity) getActivity()).addParticipant();
    }

    @OnClick(R.id.muteImageView)
    void mute() {
        AVEngineKit.CallSession session = AVEngineKit.Instance().getCurrentSession();
        if (session != null && session.getState() != AVEngineKit.CallState.Idle) {
            session.muteAudio(!micEnabled);
            micEnabled = !micEnabled;
            muteImageView.setSelected(micEnabled);
        }
    }

    @OnClick(R.id.switchCameraImageView)
    void switchCamera() {
        AVEngineKit.CallSession session = getEngineKit().getCurrentSession();
        if (session != null) {
            session.switchCamera();
        }
    }

    @OnClick(R.id.videoImageView)
    void video() {
        AVEngineKit.CallSession session = getEngineKit().getCurrentSession();
        if (session != null && session.getState() != AVEngineKit.CallState.Idle) {
            session.muteVideo(!videoEnabled);
            videoEnabled = !videoEnabled;
            videoImageView.setSelected(videoEnabled);
        }
    }

    @OnClick(R.id.hangupImageView)
    void hangup() {
        AVEngineKit.CallSession session = getEngineKit().getCurrentSession();
        if (session != null) {
            session.endCall();
        }
    }

    // hangup 触发
    @Override
    public void didCallEndWithReason(AVEngineKit.CallEndReason callEndReason) {
        // do nothing
    }

    @Override
    public void didChangeState(AVEngineKit.CallState callState) {
        AVEngineKit.CallSession callSession = AVEngineKit.Instance().getCurrentSession();
        if (callState == AVEngineKit.CallState.Connected) {
            updateParticipantStatus(callSession);
        } else if (callState == AVEngineKit.CallState.Idle) {
            getActivity().finish();
        }
        Toast.makeText(getActivity(), "" + callState.name(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void didParticipantJoined(String userId) {
        if (participants.contains(userId)) {
            return;
        }
        int count = participantLinearLayout.getChildCount();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int with = dm.widthPixels;
        for (int i = 0; i < count; i++) {
            View view = participantLinearLayout.getChildAt(i);
            if (me.uid.equals(view.getTag())) {

                UserInfo userInfo = userViewModel.getUserInfo(userId, false);
                MultiCallItem multiCallItem = new MultiCallItem(getActivity());
                multiCallItem.setTag(userInfo.uid);
                multiCallItem.setLayoutParams(new ViewGroup.LayoutParams(with / 3, with / 3));
                multiCallItem.getStatusTextView().setText(userInfo.displayName);
                multiCallItem.setOnClickListener(clickListener);
                GlideApp.with(multiCallItem).load(userInfo.portrait).into(multiCallItem.getPortraitImageView());
                participantLinearLayout.addView(multiCallItem, i);
                break;
            }
        }
        participants.add(userId);
    }

    @Override
    public void didParticipantLeft(String userId, AVEngineKit.CallEndReason callEndReason) {
        View view = participantLinearLayout.findViewWithTag(userId);
        if (view != null) {
            participantLinearLayout.removeView(view);
        }
        participants.remove(userId);

        if (userId.equals(focusVideoUserId)) {
            focusVideoUserId = null;
            focusVideoContainerFrameLayout.removeView(focusMultiCallItem);
            focusMultiCallItem = null;
        }

        Toast.makeText(getActivity(), "用户" + userId + "离开了通话", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void didChangeMode(boolean audioOnly) {

    }

    @Override
    public void didCreateLocalVideoTrack() {
        SurfaceView surfaceView = getEngineKit().getCurrentSession().createRendererView();
        if (surfaceView != null) {
            surfaceView.setZOrderMediaOverlay(false);
            surfaceView.setTag("v_" + me.uid);
            focusMultiCallItem.addView(surfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            getEngineKit().getCurrentSession().setupLocalVideo(surfaceView, scalingType);
        }
    }

    @Override
    public void didReceiveRemoteVideoTrack(String userId) {
        MultiCallItem view = participantLinearLayout.findViewWithTag(userId);
        if (view == null) {
            return;
        }
        SurfaceView surfaceView = getEngineKit().getCurrentSession().createRendererView();
        if (surfaceView != null) {
            surfaceView.setZOrderMediaOverlay(false);
            view.addView(surfaceView);
            surfaceView.setTag("v_" + userId);
            getEngineKit().getCurrentSession().setupRemoteVideo(userId, surfaceView, scalingType);
        }
    }

    @Override
    public void didRemoveRemoteVideoTrack(String userId) {
        View view = participantLinearLayout.findViewWithTag(userId);
        if (view != null) {
            participantLinearLayout.removeView(view);
        }
    }

    @Override
    public void didError(String reason) {
        Toast.makeText(getActivity(), "发生错误" + reason, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void didGetStats(StatsReport[] statsReports) {

    }

    @Override
    public void didVideoMuted(String userId, boolean enable) {
        if (enable) {
            didReceiveRemoteVideoTrack(userId);
        } else {
            didRemoveRemoteVideoTrack(userId);
        }
    }

    private View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            String userId = (String) v.getTag();
            if (!userId.equals(focusVideoUserId)) {
                MultiCallItem clickedMultiCallItem = (MultiCallItem) v;
                int clickedIndex = participantLinearLayout.indexOfChild(v);
                participantLinearLayout.removeView(clickedMultiCallItem);
                participantLinearLayout.endViewTransition(clickedMultiCallItem);
                focusVideoContainerFrameLayout.removeView(focusMultiCallItem);

                DisplayMetrics dm = getResources().getDisplayMetrics();
                int with = dm.widthPixels;
                focusVideoContainerFrameLayout.addView(clickedMultiCallItem, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                clickedMultiCallItem.setOnClickListener(null);
                participantLinearLayout.addView(focusMultiCallItem, clickedIndex, new FrameLayout.LayoutParams(with / 3, with / 3));
                focusMultiCallItem.setOnClickListener(clickListener);
                focusMultiCallItem = clickedMultiCallItem;
                focusVideoUserId = userId;

                SurfaceView surfaceView = focusMultiCallItem.findViewWithTag("v_" + focusVideoUserId);
                if (surfaceView != null) {
                    surfaceView.setZOrderOnTop(false);
                    surfaceView.setZOrderMediaOverlay(false);
                }
                bringParticipantVideoFront();

            } else {
                // do nothing

            }
        }
    };

    private void bringParticipantVideoFront() {
        int count = participantLinearLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            MultiCallItem callItem = (MultiCallItem) participantLinearLayout.getChildAt(i);
            SurfaceView surfaceView = callItem.findViewWithTag("v_" + callItem.getTag());
            if (surfaceView != null) {
                surfaceView.setZOrderMediaOverlay(true);
                surfaceView.setZOrderOnTop(true);
            }
        }

    }

    private Handler handler = new Handler();

    private void updateCallDuration() {
        AVEngineKit.CallSession session = getEngineKit().getCurrentSession();
        if (session != null && session.getState() == AVEngineKit.CallState.Connected) {
            long s = System.currentTimeMillis() - session.getConnectedTime();
            s = s / 1000;
            String text;
            if (s > 3600) {
                text = String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
            } else {
                text = String.format("%02d:%02d", s / 60, (s % 60));
            }
            durationTextView.setText(text);
        }
        handler.postDelayed(this::updateCallDuration, 1000);
    }
}