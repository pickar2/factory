package xyz.sathro.factory.test;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.Random;

public class QuestTest {
	public static final int seed = 123;
	public static final Random random = new Random();

	public static void main(String[] args) {
		for (int i = 0; i < 10; i++) {
			System.out.println(generateQuest(new IntRange(1, 3)) + "\n");
		}
	}

	public static QuestAccident generateAccident() {
		return new QuestAccident(
				QuestAccidentType.get(random),
				QuestAccidentTime.get(random)
		);
	}

	public static Quest generateQuest(IntRange accidentCount) {
		final Quest quest = new Quest(QuestType.get(random));

		for (int i = 0; i < accidentCount.getInt(random); i++) {
			quest.accidents.add(generateAccident());
		}

		return quest;
	}

	enum QuestType {
		GO_TO_LOCATION, FIND_AND_BRING_AN_ITEM, KILL_SOMEONE, INTERACT_WITH_SOMETHING, INTERACT_WITH_SOMEONE, ACCOMPANY, WAIT_TIME;

		public static QuestType get(Random random) {
			return values()[random.nextInt(values().length)];
		}
	}

	enum QuestAccidentType {
		RANDOM_ENCOUNTER, BAD_QUEST_GIVER, PUZZLE, TRAPS, ITEM_GATE, QUEST, TIME_GATE;

		public static QuestAccidentType get(Random random) {
			return values()[random.nextInt(values().length)];
		}
	}

	enum QuestAccidentTime {
		QUEST_TAKE, QUEST_IN_PROGRESS, QUEST_DESTINATION, QUEST_REWARD;

		public static QuestAccidentTime get(Random random) {
			return values()[random.nextInt(values().length)];
		}
	}

	public record IntRange(int start, int end) {
		public int getInt(Random random) {
			return start + random.nextInt(end + 1 - start);
		}
	}

	@ToString
	@AllArgsConstructor
	public static class QuestAccident {
		public final QuestAccidentType type;
		public final QuestAccidentTime time;
	}

	@RequiredArgsConstructor
	public static class Quest {
		public final QuestType questType;
		public List<QuestAccident> accidents = new ObjectArrayList<>();

		@Override
		public String toString() {
			final StringBuilder builder = new StringBuilder();

			builder.append(questType);

			if (!accidents.isEmpty()) {
				builder.append(" with {\n");
				for (int i = 0; i < accidents.size(); i++) {
					final QuestAccident accident = accidents.get(i);
					builder.append("\t").append(accident.type).append(" at ").append(accident.time);
					if (i != accidents.size() - 1) {
						builder.append(", and\n");
					}
				}
				builder.append("\n}");
			}

			return builder.toString();
		}
	}
}