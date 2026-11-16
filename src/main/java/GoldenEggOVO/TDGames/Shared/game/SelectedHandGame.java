package GoldenEggOVO.TDGames.Shared.game;

import java.util.List;

public interface SelectedHandGame extends HandGame {
    String selectionAction(int seat, List<String> ids);

    List<String> controls(int seat);
}
