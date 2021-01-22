import com.kevin.utils.DateUtils;

public class TimeTest {

    public static void main(String[] args) {
        Long timeInMs1 = 1518144381360L;
        Long timeInMs2 = 1518160549893L;

        String date = DateUtils.getDateStringByMillisecond(DateUtils.HOUR_FORMAT, timeInMs2);
        System.out.println(date);
    }


}
