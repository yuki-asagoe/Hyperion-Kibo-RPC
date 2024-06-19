package jp.jaxa.iss.kibo.rpc.defaultapk;

import android.util.Log;
import gov.nasa.arc.astrobee.Result;
import jp.jaxa.iss.kibo.rpc.api.KiboRpcService;
import jp.jaxa.iss.kibo.rpc.defaultapk.math.QuaternionUtil;

import java.util.ArrayList;
import java.util.List;

import gov.nasa.arc.astrobee.types.Point;
import gov.nasa.arc.astrobee.types.Quaternion;
import gov.nasa.arc.astrobee.Kinematics;

import org.opencv.aruco.Aruco;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.aruco.Dictionary;
import org.opencv.calib3d.Calib3d;

/**
 * Class meant to handle commands from the Ground Data System and execute them
 * in Astrobee
 */

public class YourService extends KiboRpcService {

    private final String TAG = this.getClass().getSimpleName();

    public static boolean isInKOZ(double x, double z, double[][] koz){
        double largeX = Math.max(koz[0][0], koz[1][0]);
        double smallX = Math.min(koz[0][0], koz[1][0]);
        double largeZ = Math.max(koz[0][2], koz[1][2]);
        double smallZ = Math.min(koz[0][2], koz[1][2]);

        return smallX <= x && x <= largeX && smallZ <= z && z <= largeZ;
    }

    public static double calculateDistance(double[] point1, double[] point2) {
        return Math.sqrt(Math.pow(point1[0] - point2[0], 2)
                       + Math.pow(point1[1] - point2[1], 2)
                       + Math.pow(point1[2] - point2[2], 2));
    }

    @Override
    protected void runPlan1() {
        double kiz1XMin = 10.3;
        double kiz1XMax = 11.55;
        double kiz1ZMin = 4.32;
        double kiz1ZMax = 5.57;

        // 20cm余分に取っている
        double[][] koz1Position1 = {{10.67, -9.5, 4.07}, {11.8, -9.45, 5.17}};
        double[][] koz1Position2 = {{10.05, -9.5, 4.77}, {11.07, -9.45, 5.82}};

        double[][] koz2Position1 = {{10.67, -8.5, 4.77}, {11.8, -8.45, 5.82}};
        double[][] koz2Position2 = {{10.05, -8.5, 4.07}, {10.9, -8.45, 5.17}};

        double[][] koz3Position1 = {{10.67, -7.4, 4.07}, {11.8, -7.35, 5.17}};
        double[][] koz3Position2 = {{10.05, -7.4, 4.77}, {11.07, -7.35, 5.82}};

        Log.i(TAG, "Start mission!!!");

        // The mission starts.
        api.startMission();

        // 最初の座標は(9.815, -9.806, 4.293)
        // Area1への移動(とりあえず(x,y,z_min + x,y,z_max) / 2 , 向きも適当)
        // KIZの中でKOZを避けたい
        // 特徴量の多いルートを通りたい

        Log.i(TAG, "StartLocation!!!!");
        Kinematics startLocation = api.getRobotKinematics();
        Point startLocationPoint = startLocation.getPosition();
        // ↓(10.267100213116851, -9.796862709327995, 4.270871767291918)の結果が出た
        // つまり、Astrobeeはまず最初に勝手にこの座標に移動する。よって、この座標をスタート地点として考慮する必要がある.
        Log.i(TAG, "StartLocationIs: " + startLocationPoint.getX() + ", " + startLocationPoint.getY() + ", " + startLocationPoint.getZ());

        // KIZ2からKIZ1へ移動するために、まずZ軸正方向に移動し、その後、X軸正方向に移動する
        Point kiz2ToKiz1FirstMove = new Point(10.26, -9.806, 4.56);
        // z軸負方向を軸として、90度回転
        // 視野: ｘ軸正方向 => y軸負方向
        Quaternion quaternion1 = QuaternionUtil.rotate(0, 0, -1, (float) (Math.PI * 0.5));
        Result resultMoveToKiz1FirstMove = api.moveTo(kiz2ToKiz1FirstMove, quaternion1, true);

        int loopCounterKiz2ToKiz1FirstMove = 0;
        while (!resultMoveToKiz1FirstMove.hasSucceeded() && loopCounterKiz2ToKiz1FirstMove < 5) {
            // retry
            resultMoveToKiz1FirstMove = api.moveTo(kiz2ToKiz1FirstMove, quaternion1, true);
            ++loopCounterKiz2ToKiz1FirstMove;
        }

        // 続いて、KIZ1とKIZ2の重なった部分のほぼ中心に移動する。y座標はそのまま、x座標とz座標はKIZ1とKIZ2の重なった部分の中心.
        Point kiz2ToKiz1 = new Point(10.4, -9.806, 4.56);
        Result resultMoveToEntranceOfKiz1 = api.moveTo(kiz2ToKiz1, quaternion1, true);

        int loopCounterKiz2ToKiz1 = 0;
        while (!resultMoveToEntranceOfKiz1.hasSucceeded() && loopCounterKiz2ToKiz1 < 5) {
            // retry
            resultMoveToEntranceOfKiz1 = api.moveTo(kiz2ToKiz1, quaternion1, true);
            ++loopCounterKiz2ToKiz1;
        }

        Log.i(TAG, "GetIntoKIZ1!!!!");

        // Area1の中心座標
        // Area1の中心は(10.95,−10.58,5.195)
        // とりあえず、Area1の中心から法線ベクトル上にある点に移動する
        // x座標とz座標はArea1の中心から法線ベクトル上にある点
        // y座標をArea1に近づける
        // Area1の60cm手前に移動する
        Point area1FirstViewPoint = new Point(10.95, -9.98, 5.195);
        double[] pointInFrontOfArea1Double = {10.95, -9.98, 4.595};
        Result resultMoveToArea1 = api.moveTo(area1FirstViewPoint, quaternion1, true);

        final int LOOP_MAX = 5;

        // 結果をチェックし、moveToApiが成功しない間はループする。(外乱に強いプログラム)
        int loopCounter = 0;
        while (!resultMoveToArea1.hasSucceeded() && loopCounter < LOOP_MAX) {
            // retry
            resultMoveToArea1 = api.moveTo(area1FirstViewPoint, quaternion1, true);
            ++loopCounter;
        }

        Log.i(TAG, "InFrontOfArea1!!!!");

        // Get a camera image. NavCam → 画像処理用のカメラ
        Mat image = api.getMatNavCam();

        // TODO imageがnullの場合の対処を書く

        api.saveMatImage(image, "area1.png");

        /* *********************************************************************** */
        /* 各エリアにあるアイテムの種類と数を認識するコード */
        /* *********************************************************************** */

        // ARタグを検知する
        Dictionary dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_5X5_250);
        List<Mat> corners = new ArrayList<>();
        Mat markerIds = new Mat();
        Aruco.detectMarkers(image, dictionary, corners, markerIds);

        // カメラ行列の取得
        Mat cameraMatrix = new Mat(3, 3, CvType.CV_64F);
        cameraMatrix.put(0, 0, api.getNavCamIntrinsics()[0]);
        // 歪み係数の取得
        Mat cameraCoefficients = new Mat(1, 5, CvType.CV_64F);
        cameraCoefficients.put(0, 0, api.getNavCamIntrinsics()[1]);
        cameraCoefficients.convertTo(cameraCoefficients, CvType.CV_64F);

        // 歪みのないimage
        Mat unDistortedImg = new Mat();
        Calib3d.undistort(image, unDistortedImg, cameraMatrix, cameraCoefficients);

        api.saveMatImage(unDistortedImg, "unDistortedImgOfArea1.png");

        // ARタグからカメラまでの距離と傾きを求めて、
        // 撮影した画像での座標に変換して画像用紙の部分だけを切り抜く

        // TODO 画像認識

        // AreaとItemの紐付け
        // setAreaInfo(areaId,item_name,item_number)
        api.setAreaInfo(1, "item_name", 1);

        /* **************************************************** */
        /* Let's move to the each area and recognize the items. */
        /* **************************************************** */

        /**
         * point2に移動して画像認識するコード
         */
        /**
         * KOZ1の前まで行く
         */
        // Area2(KIZ1)の5cm手前
        Point point1ToGoThroughKOZ1 = new Point(10.67, -9.475, 4.77);
        // y軸正方向を軸として、-90度回転
        // 視野: z軸負方向へ変わる
        Quaternion quaternionInFrontOfArea2 = QuaternionUtil.rotate(0, 1, 0, (float) (0.5 * Math.PI));
        Result result1MoveToKOZ1 = api.moveTo(point1ToGoThroughKOZ1, quaternionInFrontOfArea2, true);

        int loopCounter1KOZ1 = 0;
        while (!result1MoveToKOZ1.hasSucceeded() && loopCounter1KOZ1 < 5) {
            // retry
            result1MoveToKOZ1 = api.moveTo(point1ToGoThroughKOZ1, quaternionInFrontOfArea2, true);
            ++loopCounter1KOZ1;
        }

        Log.i(TAG, "InFrontOfKOZ1!!!!");

        /**
         * Area2に移動する
         */
        Point pointInFrontOfArea2 = new Point(10.925, -8.875, 4.37);
        Result resultMoveToArea2 = api.moveTo(pointInFrontOfArea2, quaternionInFrontOfArea2, true);

        int loopCounterArea2 = 0;
        while (!resultMoveToArea2.hasSucceeded() && loopCounterArea2 < 5) {
            // retry
            resultMoveToArea2 = api.moveTo(pointInFrontOfArea2, quaternionInFrontOfArea2, true);
            ++loopCounterArea2;
        }

        Log.i(TAG, "InFrontOfArea2!!!!");

        Mat image2 = api.getMatNavCam();

        // TODO image2がnullの場合の対処を書く

        api.saveMatImage(image2, "area2.png");

        /* *********************************************************************** */
        /* 各エリアにあるアイテムの種類と数を認識するコード */
        /* *********************************************************************** */

        // ARタグを検知する
        Dictionary dictionary2 = Aruco.getPredefinedDictionary(Aruco.DICT_5X5_250);
        List<Mat> corners2 = new ArrayList<>();
        Mat markerIds2 = new Mat();
        Aruco.detectMarkers(image2, dictionary2, corners2, markerIds2);

        // カメラ行列の取得
        Mat cameraMatrix2 = new Mat(3, 3, CvType.CV_64F);
        cameraMatrix2.put(0, 0, api.getNavCamIntrinsics()[0]);
        // 歪み係数の取得
        Mat cameraCoefficients2 = new Mat(1, 5, CvType.CV_64F);
        cameraCoefficients2.put(0, 0, api.getNavCamIntrinsics()[1]);
        cameraCoefficients2.convertTo(cameraCoefficients2, CvType.CV_64F);

        // 歪みのないimage
        Mat unDistortedImg2 = new Mat();
        Calib3d.undistort(image2, unDistortedImg2, cameraMatrix2, cameraCoefficients2);

        api.saveMatImage(unDistortedImg2, "unDistortedImgOfArea2.png");

        // ARタグからカメラまでの距離と傾きを求めて、
        // 撮影した画像での座標に変換して画像用紙の部分だけを切り抜く

        // TODO 画像認識

        // AreaとItemの紐付け
        // setAreaInfo(areaId,item_name,item_number)
        api.setAreaInfo(2, "item_name", 1);


        /**
         * point3に移動して画像認識するコード
         */

        // **********今回のミッションではKOZ2の前で止まる必要はなく、そのままPoint3いけばいい**********
        // **********したがって、KOZ2の前で止まるコードはコメントアウトしている**********

        /**
         * KOZ2を通過し、Area3に移動する
         */
        // Area3(KIZ1)の5cm手前
        Point pointInFrontOfArea3 = new Point(10.925, -7.925, 4.37);
        // y軸正方向を軸として、90度回転
        // 視野: z軸負方向へ変わる
        Quaternion quaternionInFrontOfArea3 = QuaternionUtil.rotate(0, 1, 0, (float) (0.5 * Math.PI));
        Result resultMoveToArea3 = api.moveTo(pointInFrontOfArea3, quaternionInFrontOfArea3, true);

        int loopCounterArea3 = 0;
        while (!resultMoveToArea3.hasSucceeded() && loopCounterArea3 < 5) {
            // retry
            resultMoveToArea3 = api.moveTo(pointInFrontOfArea3, quaternionInFrontOfArea3, true);
            ++loopCounterArea3;
        }

        Log.i(TAG, "InFrontOfArea3!!!!");

        Mat image3 = api.getMatNavCam();

        // TODO image3がnullの場合の対処を書く

        api.saveMatImage(image3, "area3.png");

        /* *********************************************************************** */
        /* 各エリアにあるアイテムの種類と数を認識するコード */
        /* *********************************************************************** */

        // ARタグを検知する
        Dictionary dictionary3 = Aruco.getPredefinedDictionary(Aruco.DICT_5X5_250);
        List<Mat> corners3 = new ArrayList<>();
        Mat markerIds3 = new Mat();
        Aruco.detectMarkers(image3, dictionary3, corners3, markerIds3);

        // カメラ行列の取得
        Mat cameraMatrix3 = new Mat(3, 3, CvType.CV_64F);
        cameraMatrix3.put(0, 0, api.getNavCamIntrinsics()[0]);
        // 歪み係数の取得
        Mat cameraCoefficients3 = new Mat(1, 5, CvType.CV_64F);
        cameraCoefficients3.put(0, 0, api.getNavCamIntrinsics()[1]);
        cameraCoefficients3.convertTo(cameraCoefficients3, CvType.CV_64F);

        // 歪みのないimage
        Mat unDistortedImg3 = new Mat();
        Calib3d.undistort(image3, unDistortedImg3, cameraMatrix3, cameraCoefficients3);

        api.saveMatImage(unDistortedImg3, "unDistortedImgOfArea3.png");

        // ARタグからカメラまでの距離と傾きを求めて、
        // 撮影した画像での座標に変換して画像用紙の部分だけを切り抜く

        //  TODO 画像認識

        // AreaとItemの紐付け
        // setAreaInfo(areaId,item_name,item_number)
        api.setAreaInfo(3, "item_name", 1);

        /**
         * point4に移動して画像認識するコード
         */
        /**
         * KOZ3の前まで行く
         */
        Point point1ToGoThroughKOZ3 = new Point(10.64, -7.375, 4.71);
        // z軸正方向を軸として、180度回転
        // 視野: x軸負方向へ変わる
        Quaternion quaternionInFrontOfArea4 = QuaternionUtil.rotate(0, 0, 1, (float) (Math.PI));
        Result result1MoveToKOZ3 = api.moveTo(point1ToGoThroughKOZ3, quaternionInFrontOfArea4, true);

        int loopCounter1KOZ3 = 0;
        while (!result1MoveToKOZ3.hasSucceeded() && loopCounter1KOZ3 < 5) {
            // retry
            result1MoveToKOZ3 = api.moveTo(point1ToGoThroughKOZ3, quaternionInFrontOfArea4, true);
            ++loopCounter1KOZ3;
        }

        Log.i(TAG, "InFrontOfKOZ3!!!!");

        /**
         * Area4に移動する
         */
        // Area4の60cm手前
        Point pointInFrontOfArea4 = new Point(10.46, -6.9875, 4.945);
        Result resultMoveToArea4 = api.moveTo(pointInFrontOfArea4, quaternionInFrontOfArea4, true);

        int loopCounterArea4 = 0;
        while (!resultMoveToArea4.hasSucceeded() && loopCounterArea4 < 5) {
            // retry
            resultMoveToArea4 = api.moveTo(pointInFrontOfArea4, quaternionInFrontOfArea4, true);
            ++loopCounterArea4;
        }

        Log.i(TAG, "InFrontOfArea4!!!!");

        Mat image4 = api.getMatNavCam();

        // TODO image4がnullの場合の対処を書く

        api.saveMatImage(image4, "area4.png");

        /* *********************************************************************** */
        /* 各エリアにあるアイテムの種類と数を認識するコード */
        /* *********************************************************************** */

        // ARタグを検知する
        Dictionary dictionary4 = Aruco.getPredefinedDictionary(Aruco.DICT_5X5_250);
        List<Mat> corners4 = new ArrayList<>();
        Mat markerIds4 = new Mat();
        Aruco.detectMarkers(image4, dictionary4, corners4, markerIds4);

        // カメラ行列の取得
        Mat cameraMatrix4 = new Mat(3, 3, CvType.CV_64F);
        cameraMatrix4.put(0, 0, api.getNavCamIntrinsics()[0]);
        // 歪み係数の取得
        Mat cameraCoefficients4 = new Mat(1, 5, CvType.CV_64F);
        cameraCoefficients4.put(0, 0, api.getNavCamIntrinsics()[1]);
        cameraCoefficients4.convertTo(cameraCoefficients4, CvType.CV_64F);

        // 歪みのないimage
        Mat unDistortedImg4 = new Mat();
        Calib3d.undistort(image4, unDistortedImg4, cameraMatrix4, cameraCoefficients4);

        api.saveMatImage(unDistortedImg4, "unDistortedImgOfArea4.png");

        // ARタグからカメラまでの距離と傾きを求めて、
        // 撮影した画像での座標に変換して画像用紙の部分だけを切り抜く

        // TODO 画像認識

        // AreaとItemの紐付け
        // setAreaInfo(areaId,item_name,item_number)
        api.setAreaInfo(4, "item_name", 1);

        /**
         * 宇宙飛行士の前へ移動して画像認識するコード
         */

        Point pointInFrontOfAstronaut = new Point(11.143, -6.7607, 4.9654);
        // z軸正方向を軸として、90度回転
        // 視野: y軸正方向へ変わる
        Quaternion quaternionInFrontOfAstronaut = QuaternionUtil.rotate(0, 0, 1, (float) (0.5 * Math.PI));
        Result resultMoveToAstronaut = api.moveTo(pointInFrontOfAstronaut, quaternionInFrontOfAstronaut, true);

        int loopCounterAstronaut = 0;
        while (!resultMoveToAstronaut.hasSucceeded() && loopCounterAstronaut < 5) {
            // retry
            resultMoveToAstronaut = api.moveTo(pointInFrontOfAstronaut, quaternionInFrontOfAstronaut, true);
            ++loopCounterAstronaut;
        }

        // 宇宙飛行士の前にたどり着いたことを通知、画像を取得
        api.reportRoundingCompletion();

        Log.i(TAG, "InFrontOfAstronaut!!!!");

        Mat imageAstronaut = api.getMatNavCam();

        // imageAstronautがnullの場合の対処を書く

        api.saveMatImage(imageAstronaut, "astronaut.png");

        // ARタグを検知する
        Dictionary dictionaryAstronaut = Aruco.getPredefinedDictionary(Aruco.DICT_5X5_250);
        List<Mat> cornersAstronaut = new ArrayList<>();
        Mat markerIdsAstronaut = new Mat();
        Aruco.detectMarkers(imageAstronaut, dictionaryAstronaut, cornersAstronaut, markerIdsAstronaut);

        int loopCounterAstronautImage = 0;
        while (markerIdsAstronaut.empty() && loopCounterAstronautImage < 10) {
            imageAstronaut = api.getMatNavCam();
            api.saveMatImage(imageAstronaut, "astronaut.png");
            dictionaryAstronaut = Aruco.getPredefinedDictionary(Aruco.DICT_5X5_250);
            cornersAstronaut = new ArrayList<>();
            markerIdsAstronaut = new Mat();
            Aruco.detectMarkers(imageAstronaut, dictionaryAstronaut, cornersAstronaut, markerIdsAstronaut);
            loopCounterAstronautImage++;
        }

        // カメラ行列の取得
        Mat cameraMatrixAstronaut = new Mat(3, 3, CvType.CV_64F);
        cameraMatrixAstronaut.put(0, 0, api.getNavCamIntrinsics()[0]);
        // 歪み係数の取得
        Mat cameraCoefficientsAstronaut = new Mat(1, 5, CvType.CV_64F);
        cameraCoefficientsAstronaut.put(0, 0, api.getNavCamIntrinsics()[1]);
        cameraCoefficientsAstronaut.convertTo(cameraCoefficientsAstronaut, CvType.CV_64F);

        // 歪みのないimage
        Mat unDistortedImgAstronaut = new Mat();
        Calib3d.undistort(imageAstronaut, unDistortedImgAstronaut, cameraMatrixAstronaut, cameraCoefficientsAstronaut);

        api.saveMatImage(unDistortedImgAstronaut, "unDistortedImgOfAreaAstronaut.png");

        // ARタグからカメラまでの距離と傾きを求めて、
        // 撮影した画像での座標に変換して画像用紙の部分だけを切り抜く

        // TODO 画像認識

        /* ********************************************************** */
        /* Write your code to recognize which item the astronaut has. */
        /* ********************************************************** */

        // 宇宙飛行士の持つTargetItemを認識したことを通知
        api.notifyRecognitionItem();

        /*
         * *****************************************************************************
         * **************************
         */
        /*
         * Write your code to move Astrobee to the location of the target item (what the
         * astronaut is looking for)
         */

        int targetItemID;
        // 暫定で1にしている
        targetItemID = 1;

        // 現在の座標を取得

        /**
         * KOZ3の前まで行く
         */
        // 得られた最短距離の座標に移動
        Point point1ToGoThroughKOZ3WhenReturn = new Point(10.65, -7.375, 4.72);
        Result result1MoveToKOZ3WhenReturn = api.moveTo(point1ToGoThroughKOZ3WhenReturn, quaternion1, true);

        int loopCounter1KOZ3WhenReturn = 0;
        while (!result1MoveToKOZ3WhenReturn.hasSucceeded() && loopCounter1KOZ3WhenReturn < 5) {
            // retry
            result1MoveToKOZ3WhenReturn = api.moveTo(point1ToGoThroughKOZ3WhenReturn, quaternion1, true);
            ++loopCounter1KOZ3WhenReturn;
        }

        Log.i(TAG, "InFrontOfKOZ3WhenReturn!!!!");

        /**
         * KOZ2の前まで行く
         */
        // 得られた最短距離の座標に移動
        Point point1ToGoThroughKOZ2WhenReturn = new Point(10.9, -8.475, 4.72);
        Result result1MoveToKOZ2WhenReturn = api.moveTo(point1ToGoThroughKOZ2WhenReturn, quaternion1, true);

        int loopCounter1KOZ2WhenReturn = 0;
        while (!result1MoveToKOZ2WhenReturn.hasSucceeded() && loopCounter1KOZ2WhenReturn < 5) {
            // retry
            result1MoveToKOZ2WhenReturn = api.moveTo(point1ToGoThroughKOZ2WhenReturn, quaternion1, true);
            ++loopCounter1KOZ2WhenReturn;
        }

        Log.i(TAG, "InFrontOfKOZ2WhenReturn!!!!");

        /**
         * KOZ1の前まで行く
         */
        // 得られた最短距離の座標に移動
        Point point1ToGoThroughKOZ1WhenReturn = new Point(11.1, -9.475, 5.22);
        Result result1MoveToKOZ1WhenReturn = api.moveTo(point1ToGoThroughKOZ1WhenReturn, quaternion1, true);

        int loopCounter1KOZ1WhenReturn = 0;
        while (!result1MoveToKOZ1WhenReturn.hasSucceeded() && loopCounter1KOZ1WhenReturn < 5) {
            // retry
            result1MoveToKOZ1WhenReturn = api.moveTo(point1ToGoThroughKOZ1WhenReturn, quaternion1, true);
            ++loopCounter1KOZ1WhenReturn;
        }

        Log.i(TAG, "InFrontOfKOZ1WhenReturn!!!!");

        /**
         * Area1に移動する
         */
        resultMoveToArea1 = api.moveTo(area1FirstViewPoint, quaternion1, true);

        int loopCounterArea1WhenReturn = 0;
        while (!resultMoveToArea1.hasSucceeded() && loopCounterArea1WhenReturn < 5) {
            // retry
            resultMoveToArea1 = api.moveTo(area1FirstViewPoint, quaternion1, true);
            ++loopCounterArea1WhenReturn;
        }

        Log.i(TAG, "InFrontOfArea1WhenReturn!!!!");

        Mat imageAfterReturn = api.getMatNavCam();
        api.saveMatImage(imageAfterReturn, "afterReturn.png");

        /*
         * *****************************************************************************
         * **************************
         */

        // Take a snapshot of the target item.→ミッション終了
        api.takeTargetItemSnapshot();

    }

    @Override
    protected void runPlan2() {
        // write your plan 2 here
    }

    @Override
    protected void runPlan3() {
        // write your plan 3 here
    }

    // You can add your method.
    private String yourMethod() {
        return "your method";
    }
}
